package com.devmind.module.search.vectorstore;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class PgVectorStoreIntegrationTest {

    private static final int DIMENSION = 4;

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("devmind_vectors")
            .withUsername("devmind")
            .withPassword("devmind");

    private static JdbcTemplate jdbcTemplate;
    private static PgVectorStore store;

    @BeforeAll
    static void initStore() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbcTemplate = new JdbcTemplate(dataSource);
        store = new PgVectorStore(jdbcTemplate, DIMENSION);
        store.initSchema();
    }

    @BeforeEach
    void cleanTable() {
        jdbcTemplate.update("DELETE FROM knowledge_chunk_embedding");
    }

    @Test
    void searchShouldOrderByCosineSimilarity() {
        store.upsertChunkVector(1L, 100L, 11L, "remote-dense", new float[]{1f, 0f, 0f, 0f});
        store.upsertChunkVector(1L, 100L, 12L, "remote-dense", new float[]{0.6f, 0.8f, 0f, 0f});
        store.upsertChunkVector(1L, 100L, 13L, "remote-dense", new float[]{0f, 0f, 1f, 0f});

        List<PgVectorStore.ChunkVectorMatch> matches =
                store.searchSimilar(1L, "remote-dense", new float[]{1f, 0f, 0f, 0f}, 3);

        assertThat(matches).extracting(PgVectorStore.ChunkVectorMatch::chunkId)
                .containsExactly(11L, 12L, 13L);
        assertThat(matches.get(0).similarity()).isGreaterThan(0.99);
        assertThat(matches.get(1).similarity()).isBetween(0.5, 0.7);
    }

    @Test
    void upsertShouldReplaceExistingRowPerChunkAndProvider() {
        store.upsertChunkVector(1L, 100L, 11L, "remote-dense", new float[]{1f, 0f, 0f, 0f});
        store.upsertChunkVector(1L, 100L, 11L, "remote-dense", new float[]{0f, 1f, 0f, 0f});

        Integer rowCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM knowledge_chunk_embedding WHERE chunk_id = 11", Integer.class);
        assertThat(rowCount).isEqualTo(1);

        List<PgVectorStore.ChunkVectorMatch> matches =
                store.searchSimilar(1L, "remote-dense", new float[]{0f, 1f, 0f, 0f}, 1);
        assertThat(matches.get(0).similarity()).isGreaterThan(0.99);
    }

    @Test
    void searchShouldBeScopedToUser() {
        store.upsertChunkVector(1L, 100L, 11L, "remote-dense", new float[]{1f, 0f, 0f, 0f});

        assertThat(store.searchSimilar(2L, "remote-dense", new float[]{1f, 0f, 0f, 0f}, 5)).isEmpty();
    }

    @Test
    void archiveByDocumentShouldHideRowsFromSearch() {
        store.upsertChunkVector(1L, 100L, 11L, "remote-dense", new float[]{1f, 0f, 0f, 0f});
        store.archiveByDocument(1L, 100L);

        assertThat(store.searchSimilar(1L, "remote-dense", new float[]{1f, 0f, 0f, 0f}, 5)).isEmpty();
    }

    @Test
    void dimensionAboveHnswCapShouldFailFast() {
        assertThatThrownBy(() -> new PgVectorStore(jdbcTemplate, 2048))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2000");
    }
}
