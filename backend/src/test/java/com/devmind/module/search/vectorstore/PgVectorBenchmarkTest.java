package com.devmind.module.search.vectorstore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Measures the storage/serving tradeoff this project talks about in its README:
 * brute-force cosine over all vectors (what the MySQL-JSON path does, minus SQL fetch
 * and JSON decode, so its numbers are a LOWER bound for that path) versus a pgvector
 * HNSW index (approximate search, so recall@10 against exact top-10 is also reported).
 *
 * <p>Not part of the default suite — run explicitly with:
 * {@code ./mvnw test -Dtest=PgVectorBenchmarkTest -Ddevmind.benchmark=true}
 * and optionally {@code -Ddevmind.benchmark.vectorCount=100000}.</p>
 */
@Testcontainers(disabledWithoutDocker = true)
@EnabledIfSystemProperty(named = "devmind.benchmark", matches = "true")
class PgVectorBenchmarkTest {

    private static final int DIMENSION = 1024;
    private static final int TOP_K = 10;
    private static final int QUERY_COUNT = 50;
    private static final int BATCH_SIZE = 500;

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("bench")
            .withUsername("bench")
            .withPassword("bench");

    @Test
    void compareBruteForceAgainstHnsw() {
        int vectorCount = Integer.getInteger("devmind.benchmark.vectorCount", 10_000);
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));

        jdbc.execute("CREATE EXTENSION IF NOT EXISTS vector");
        jdbc.execute("CREATE TABLE bench_embedding (id BIGINT PRIMARY KEY, embedding vector(" + DIMENSION + ") NOT NULL)");

        Random random = new Random(42);
        float[][] corpus = new float[vectorCount][];
        long insertStart = System.nanoTime();
        List<Object[]> batch = new ArrayList<>(BATCH_SIZE);
        for (int id = 0; id < vectorCount; id++) {
            corpus[id] = randomUnitVector(random);
            batch.add(new Object[]{id, DenseVectorCodec.toVectorLiteral(corpus[id])});
            if (batch.size() == BATCH_SIZE || id == vectorCount - 1) {
                jdbc.batchUpdate("INSERT INTO bench_embedding (id, embedding) VALUES (?, ?::vector)", batch);
                batch.clear();
            }
        }
        long insertMillis = (System.nanoTime() - insertStart) / 1_000_000;

        // Bulk-load first, index afterwards: building HNSW incrementally during inserts
        // is much slower than one graph build over the finished data set.
        long indexStart = System.nanoTime();
        jdbc.execute("CREATE INDEX ON bench_embedding USING hnsw (embedding vector_cosine_ops)");
        long indexMillis = (System.nanoTime() - indexStart) / 1_000_000;

        float[][] queries = new float[QUERY_COUNT][];
        for (int i = 0; i < QUERY_COUNT; i++) {
            queries[i] = randomUnitVector(random);
        }

        long[] bruteNanos = new long[QUERY_COUNT];
        List<Set<Long>> exactTopK = new ArrayList<>(QUERY_COUNT);
        for (int i = 0; i < QUERY_COUNT; i++) {
            long start = System.nanoTime();
            exactTopK.add(exactTopK(corpus, queries[i]));
            bruteNanos[i] = System.nanoTime() - start;
        }

        long[] annNanos = new long[QUERY_COUNT];
        double recallSum = 0;
        for (int i = 0; i < QUERY_COUNT; i++) {
            String literal = DenseVectorCodec.toVectorLiteral(queries[i]);
            long start = System.nanoTime();
            List<Long> annIds = jdbc.queryForList(
                    "SELECT id FROM bench_embedding ORDER BY embedding <=> ?::vector LIMIT " + TOP_K,
                    Long.class,
                    literal);
            annNanos[i] = System.nanoTime() - start;
            Set<Long> exact = exactTopK.get(i);
            recallSum += annIds.stream().filter(exact::contains).count() / (double) TOP_K;
        }
        double recallAtK = recallSum / QUERY_COUNT;

        System.out.printf("BENCHMARK pgvector N=%d dim=%d topK=%d queries=%d%n",
                vectorCount, DIMENSION, TOP_K, QUERY_COUNT);
        System.out.printf("  bulk insert          : %d ms%n", insertMillis);
        System.out.printf("  hnsw index build     : %d ms%n", indexMillis);
        System.out.printf("  brute-force (in-mem) : P50=%.2f ms  P99=%.2f ms%n",
                percentileMillis(bruteNanos, 50), percentileMillis(bruteNanos, 99));
        System.out.printf("  pgvector hnsw        : P50=%.2f ms  P99=%.2f ms%n",
                percentileMillis(annNanos, 50), percentileMillis(annNanos, 99));
        System.out.printf("  recall@%d vs exact   : %.4f%n", TOP_K, recallAtK);

        assertThat(recallAtK).isGreaterThan(0.8);
    }

    private float[] randomUnitVector(Random random) {
        float[] vector = new float[DIMENSION];
        double norm = 0;
        for (int i = 0; i < DIMENSION; i++) {
            vector[i] = (float) random.nextGaussian();
            norm += vector[i] * vector[i];
        }
        float scale = (float) (1.0 / Math.sqrt(norm));
        for (int i = 0; i < DIMENSION; i++) {
            vector[i] *= scale;
        }
        return vector;
    }

    private Set<Long> exactTopK(float[][] corpus, float[] query) {
        long[] ids = new long[corpus.length];
        double[] scores = new double[corpus.length];
        for (int id = 0; id < corpus.length; id++) {
            double dot = 0;
            float[] row = corpus[id];
            for (int i = 0; i < DIMENSION; i++) {
                dot += row[i] * query[i];
            }
            ids[id] = id;
            scores[id] = dot;
        }
        // partial selection of top K
        Set<Long> top = new HashSet<>();
        for (int k = 0; k < TOP_K; k++) {
            int best = -1;
            double bestScore = -Double.MAX_VALUE;
            for (int id = 0; id < corpus.length; id++) {
                if (scores[id] > bestScore && !top.contains(ids[id])) {
                    bestScore = scores[id];
                    best = id;
                }
            }
            top.add(ids[best]);
        }
        return top;
    }

    private double percentileMillis(long[] nanos, int percentile) {
        long[] sorted = Arrays.copyOf(nanos, nanos.length);
        Arrays.sort(sorted);
        int index = Math.min(sorted.length - 1, (int) Math.ceil(percentile / 100.0 * sorted.length) - 1);
        return sorted[Math.max(index, 0)] / 1_000_000.0;
    }
}
