package com.devmind.module.search.vectorstore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
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
 * and optionally {@code -Ddevmind.benchmark.vectorCount=100000}.
 * By default a throwaway pgvector container is managed by Testcontainers; on hosts
 * where Testcontainers cannot talk to the Docker daemon, point the benchmark at an
 * externally started Postgres instead:
 * {@code -Ddevmind.benchmark.jdbcUrl=jdbc:postgresql://localhost:5434/bench
 *  -Ddevmind.benchmark.username=bench -Ddevmind.benchmark.password=bench}.</p>
 */
@EnabledIfSystemProperty(named = "devmind.benchmark", matches = "true")
class PgVectorBenchmarkTest {

    private static final int DIMENSION = 1024;
    private static final int TOP_K = 10;
    private static final int QUERY_COUNT = 50;
    private static final int BATCH_SIZE = 500;
    private static final int CLUSTER_COUNT = 64;
    private static final float CLUSTER_NOISE = 0.35f;

    @Test
    void compareBruteForceAgainstHnsw() {
        int vectorCount = Integer.getInteger("devmind.benchmark.vectorCount", 10_000);
        String externalJdbcUrl = System.getProperty("devmind.benchmark.jdbcUrl");
        PostgreSQLContainer<?> container = null;
        JdbcTemplate jdbc;
        if (externalJdbcUrl != null && !externalJdbcUrl.isBlank()) {
            jdbc = new JdbcTemplate(new DriverManagerDataSource(
                    externalJdbcUrl,
                    System.getProperty("devmind.benchmark.username", "bench"),
                    System.getProperty("devmind.benchmark.password", "bench")));
        } else {
            container = new PostgreSQLContainer<>(
                    DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("bench")
                    .withUsername("bench")
                    .withPassword("bench");
            container.start();
            jdbc = new JdbcTemplate(new DriverManagerDataSource(
                    container.getJdbcUrl(), container.getUsername(), container.getPassword()));
        }

        try {
            runBenchmark(jdbc, vectorCount);
        } finally {
            if (container != null) {
                container.stop();
            }
        }
    }

    private void runBenchmark(JdbcTemplate jdbc, int vectorCount) {
        jdbc.execute("CREATE EXTENSION IF NOT EXISTS vector");
        jdbc.execute("DROP TABLE IF EXISTS bench_embedding");
        jdbc.execute("CREATE TABLE bench_embedding (id BIGINT PRIMARY KEY, embedding vector(" + DIMENSION + ") NOT NULL)");

        // Uniformly random high-dim vectors have no manifold structure: all pairs are
        // near-orthogonal, nearest neighbors are statistically meaningless, and HNSW's
        // greedy graph walk degrades to ~0.25 recall (measured here before this fix).
        // Real embeddings are clustered, so the synthetic corpus is drawn from cluster
        // centers plus noise to approximate that geometry.
        Random random = new Random(42);
        float[][] centers = new float[CLUSTER_COUNT][];
        for (int c = 0; c < CLUSTER_COUNT; c++) {
            centers[c] = randomUnitVector(random);
        }
        float[][] corpus = new float[vectorCount][];
        long insertStart = System.nanoTime();
        List<Object[]> batch = new ArrayList<>(BATCH_SIZE);
        for (int id = 0; id < vectorCount; id++) {
            corpus[id] = clusteredVector(centers[random.nextInt(CLUSTER_COUNT)], random);
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
            queries[i] = clusteredVector(centers[random.nextInt(CLUSTER_COUNT)], random);
        }

        long[] bruteNanos = new long[QUERY_COUNT];
        List<Set<Long>> exactTopK = new ArrayList<>(QUERY_COUNT);
        for (int i = 0; i < QUERY_COUNT; i++) {
            long start = System.nanoTime();
            exactTopK.add(exactTopK(corpus, queries[i]));
            bruteNanos[i] = System.nanoTime() - start;
        }

        AnnRun defaultEf = runAnnQueries(jdbc, queries, exactTopK, null);
        AnnRun boostedEf = runAnnQueries(jdbc, queries, exactTopK, 120);

        System.out.printf("BENCHMARK pgvector N=%d dim=%d topK=%d queries=%d clusters=%d%n",
                vectorCount, DIMENSION, TOP_K, QUERY_COUNT, CLUSTER_COUNT);
        System.out.printf("  bulk insert            : %d ms%n", insertMillis);
        System.out.printf("  hnsw index build       : %d ms%n", indexMillis);
        System.out.printf("  brute-force (in-mem)   : P50=%.2f ms  P99=%.2f ms%n",
                percentileMillis(bruteNanos, 50), percentileMillis(bruteNanos, 99));
        System.out.printf("  hnsw ef_search=40(def) : P50=%.2f ms  P99=%.2f ms  recall@%d=%.4f%n",
                percentileMillis(defaultEf.nanos, 50), percentileMillis(defaultEf.nanos, 99), TOP_K, defaultEf.recall);
        System.out.printf("  hnsw ef_search=120     : P50=%.2f ms  P99=%.2f ms  recall@%d=%.4f%n",
                percentileMillis(boostedEf.nanos, 50), percentileMillis(boostedEf.nanos, 99), TOP_K, boostedEf.recall);

        assertThat(boostedEf.recall).isGreaterThan(0.8);
    }

    private record AnnRun(long[] nanos, double recall) {
    }

    /**
     * Runs all ANN queries on a single connection so that a session-level
     * {@code SET hnsw.ef_search} actually applies to the measured statements.
     */
    private AnnRun runAnnQueries(JdbcTemplate jdbc,
                                 float[][] queries,
                                 List<Set<Long>> exactTopK,
                                 Integer efSearch) {
        long[] nanos = new long[queries.length];
        double[] recallSum = {0};
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
            try (java.sql.Statement statement = connection.createStatement()) {
                if (efSearch != null) {
                    statement.execute("SET hnsw.ef_search = " + efSearch);
                }
            } catch (java.sql.SQLException ex) {
                throw new IllegalStateException(ex);
            }
            try (java.sql.PreparedStatement select = connection.prepareStatement(
                    "SELECT id FROM bench_embedding ORDER BY embedding <=> ?::vector LIMIT " + TOP_K)) {
                // Unmeasured warmup so the first measured run does not pay JIT/cache costs.
                for (int i = 0; i < Math.min(5, queries.length); i++) {
                    select.setString(1, DenseVectorCodec.toVectorLiteral(queries[i]));
                    try (java.sql.ResultSet resultSet = select.executeQuery()) {
                        while (resultSet.next()) {
                            resultSet.getLong(1);
                        }
                    }
                }
                for (int i = 0; i < queries.length; i++) {
                    select.setString(1, DenseVectorCodec.toVectorLiteral(queries[i]));
                    long start = System.nanoTime();
                    List<Long> annIds = new ArrayList<>(TOP_K);
                    try (java.sql.ResultSet resultSet = select.executeQuery()) {
                        while (resultSet.next()) {
                            annIds.add(resultSet.getLong(1));
                        }
                    }
                    nanos[i] = System.nanoTime() - start;
                    Set<Long> exact = exactTopK.get(i);
                    recallSum[0] += annIds.stream().filter(exact::contains).count() / (double) TOP_K;
                }
            } catch (java.sql.SQLException ex) {
                throw new IllegalStateException(ex);
            }
            return null;
        });
        return new AnnRun(nanos, recallSum[0] / queries.length);
    }

    private float[] clusteredVector(float[] center, Random random) {
        // The noise budget is a fraction of the WHOLE vector norm, not a per-component
        // sigma: with per-dimension noise of the same order as the unit center, the
        // noise norm grows with sqrt(dim) (~32x here) and drowns the cluster structure.
        float[] noise = randomUnitVector(random);
        float[] vector = new float[DIMENSION];
        double norm = 0;
        for (int i = 0; i < DIMENSION; i++) {
            vector[i] = center[i] + CLUSTER_NOISE * noise[i];
            norm += vector[i] * vector[i];
        }
        float scale = (float) (1.0 / Math.sqrt(norm));
        for (int i = 0; i < DIMENSION; i++) {
            vector[i] *= scale;
        }
        return vector;
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
