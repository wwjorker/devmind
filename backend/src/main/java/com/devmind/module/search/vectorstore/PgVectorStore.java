package com.devmind.module.search.vectorstore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * Dense-vector serving index backed by Postgres + pgvector (HNSW, cosine distance).
 *
 * <p>Design notes:
 * <ul>
 *   <li>MySQL remains the source of truth; rows here are derived, rebuildable data.
 *     Writes are best-effort double-writes — a lost row only degrades recall until the
 *     next rebuild/backfill, it never corrupts primary data.</li>
 *   <li>The HNSW index on the {@code vector} type supports at most 2000 dimensions,
 *     which is why the remote embedding is requested at a reduced dimension (1024).
 *     The constructor fails fast instead of letting index creation fail at runtime.</li>
 *   <li>Tenant filtering ({@code user_id}) is applied as a WHERE clause on top of the
 *     ANN scan. pgvector post-filters HNSW results, so heavily filtered queries can
 *     return fewer than {@code limit} rows — acceptable here because every query is
 *     scoped to one user whose vectors dominate their own partition.</li>
 * </ul>
 */
public class PgVectorStore implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(PgVectorStore.class);
    private static final int HNSW_MAX_DIMENSION = 2000;
    private static final int STATUS_ACTIVE = 1;

    private final JdbcTemplate jdbcTemplate;
    private final int dimension;
    private final AutoCloseable connectionPool;

    public PgVectorStore(JdbcTemplate jdbcTemplate, int dimension) {
        this(jdbcTemplate, dimension, null);
    }

    public PgVectorStore(JdbcTemplate jdbcTemplate, int dimension, AutoCloseable connectionPool) {
        if (dimension <= 0 || dimension > HNSW_MAX_DIMENSION) {
            throw new IllegalArgumentException(
                    "pgvector HNSW supports 1.." + HNSW_MAX_DIMENSION + " dimensions but got " + dimension
                            + "; lower the embedding dimension (e.g. request 1024 from the provider)");
        }
        this.jdbcTemplate = jdbcTemplate;
        this.dimension = dimension;
        this.connectionPool = connectionPool;
    }

    @Override
    public void close() {
        if (connectionPool != null) {
            try {
                connectionPool.close();
            } catch (Exception ex) {
                log.warn("Failed to close pgvector connection pool", ex);
            }
        }
    }

    public void initSchema() {
        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS knowledge_chunk_embedding (
                    id BIGSERIAL PRIMARY KEY,
                    chunk_id BIGINT NOT NULL,
                    document_id BIGINT NOT NULL,
                    user_id BIGINT NOT NULL,
                    provider_name VARCHAR(64) NOT NULL,
                    embedding vector(%d) NOT NULL,
                    status SMALLINT NOT NULL DEFAULT 1,
                    updated_at TIMESTAMP NOT NULL DEFAULT now(),
                    CONSTRAINT uk_chunk_embedding_chunk_provider UNIQUE (chunk_id, provider_name)
                )
                """.formatted(dimension));
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_chunk_embedding_user
                ON knowledge_chunk_embedding (user_id, provider_name, status)
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_chunk_embedding_hnsw
                ON knowledge_chunk_embedding USING hnsw (embedding vector_cosine_ops)
                """);
        log.info("pgvector store ready. dimension={}", dimension);
    }

    public int dimension() {
        return dimension;
    }

    public void upsertChunkVector(Long userId,
                                  Long documentId,
                                  Long chunkId,
                                  String providerName,
                                  float[] embedding) {
        jdbcTemplate.update("""
                        INSERT INTO knowledge_chunk_embedding
                            (chunk_id, document_id, user_id, provider_name, embedding, status, updated_at)
                        VALUES (?, ?, ?, ?, ?::vector, 1, now())
                        ON CONFLICT (chunk_id, provider_name) DO UPDATE SET
                            embedding = EXCLUDED.embedding,
                            document_id = EXCLUDED.document_id,
                            user_id = EXCLUDED.user_id,
                            status = 1,
                            updated_at = now()
                        """,
                chunkId, documentId, userId, providerName, DenseVectorCodec.toVectorLiteral(embedding));
    }

    public void archiveByDocument(Long userId, Long documentId) {
        jdbcTemplate.update("""
                        UPDATE knowledge_chunk_embedding
                        SET status = 0, updated_at = now()
                        WHERE user_id = ? AND document_id = ? AND status = 1
                        """,
                userId, documentId);
    }

    public List<ChunkVectorMatch> searchSimilar(Long userId,
                                                String providerName,
                                                float[] queryVector,
                                                int limit) {
        String literal = DenseVectorCodec.toVectorLiteral(queryVector);
        return jdbcTemplate.query("""
                        SELECT chunk_id, 1 - (embedding <=> ?::vector) AS similarity
                        FROM knowledge_chunk_embedding
                        WHERE user_id = ? AND provider_name = ? AND status = ?
                        ORDER BY embedding <=> ?::vector
                        LIMIT ?
                        """,
                (rs, rowNum) -> new ChunkVectorMatch(rs.getLong("chunk_id"), rs.getDouble("similarity")),
                literal, userId, providerName, STATUS_ACTIVE, literal, Math.max(1, limit));
    }

    public record ChunkVectorMatch(Long chunkId, double similarity) {
    }
}
