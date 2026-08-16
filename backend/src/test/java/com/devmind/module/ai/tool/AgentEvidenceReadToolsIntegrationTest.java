package com.devmind.module.ai.tool;

import com.devmind.common.exception.BizException;
import com.devmind.module.ai.agent.AgentToolContext;
import com.devmind.module.ai.agent.AgentToolDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.url=jdbc:h2:mem:agent_evidence_tools_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "devmind.ai.provider=mock"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AgentEvidenceReadToolsIntegrationTest {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final GetChunkEvidenceReadTool chunkTool;
    private final GetAskLogEvidenceReadTool askLogTool;
    private final AgentReadToolRegistry registry;

    @Autowired
    AgentEvidenceReadToolsIntegrationTest(JdbcTemplate jdbcTemplate,
                                          ObjectMapper objectMapper,
                                          GetChunkEvidenceReadTool chunkTool,
                                          GetAskLogEvidenceReadTool askLogTool,
                                          AgentReadToolRegistry registry) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.chunkTool = chunkTool;
        this.askLogTool = askLogTool;
        this.registry = registry;
    }

    @BeforeAll
    void createSchemaAndFixtures() {
        jdbcTemplate.execute("""
                CREATE TABLE knowledge_document (
                    id BIGINT PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    title VARCHAR(120) NOT NULL,
                    content CLOB NOT NULL,
                    source_type VARCHAR(32) NOT NULL,
                    tags VARCHAR(255),
                    summary VARCHAR(500),
                    status TINYINT NOT NULL,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE knowledge_document_chunk (
                    id BIGINT PRIMARY KEY,
                    document_id BIGINT NOT NULL,
                    user_id BIGINT NOT NULL,
                    chunk_index INT NOT NULL,
                    content CLOB NOT NULL,
                    token_count INT NOT NULL,
                    status TINYINT NOT NULL,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE ai_ask_log (
                    id BIGINT PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    question VARCHAR(500) NOT NULL,
                    retrieval_keyword VARCHAR(128) NOT NULL,
                    prompt_preview CLOB,
                    prompt_schema_version TINYINT NOT NULL,
                    answer CLOB NOT NULL,
                    model_provider VARCHAR(64) NOT NULL,
                    mock TINYINT NOT NULL,
                    prompt_tokens INT,
                    completion_tokens INT,
                    total_tokens INT,
                    retrieved_chunk_count INT NOT NULL,
                    retrieved_chunk_ids VARCHAR(500),
                    elapsed_ms BIGINT NOT NULL,
                    status TINYINT NOT NULL,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.update("""
                INSERT INTO knowledge_document
                    (id, user_id, title, content, source_type, tags, summary, status)
                VALUES (10, 7, 'Archived Spring note', 'body', 'java_note', 'spring,tx', NULL, 0),
                       (20, 8, 'Other tenant note', 'body', 'java_note', NULL, NULL, 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO knowledge_document_chunk
                    (id, document_id, user_id, chunk_index, content, token_count, status)
                VALUES (101, 10, 7, 0, 'Historical transaction evidence', 15, 0),
                       (201, 20, 8, 0, 'Other tenant secret', 10, 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO ai_ask_log
                    (id, user_id, question, retrieval_keyword, prompt_preview,
                     prompt_schema_version, answer, model_provider, mock,
                     retrieved_chunk_count, retrieved_chunk_ids, elapsed_ms, status)
                VALUES (301, 7, 'How does propagation work?', 'propagation', 'bounded preview',
                        1, ?, 'mock', 1, 1, '101', 5, 1),
                       (302, 8, 'private question', 'private', NULL,
                        2, 'private answer', 'mock', 1, 1, '201', 5, 1)
                """, "A".repeat(4_100));
    }

    @Test
    void shouldReadOwnedArchivedChunkWithoutLeakingOtherTenant() throws Exception {
        JsonNode result = chunkTool.execute(
                new AgentToolContext(7L, 1L),
                objectMapper.readTree("{\"chunkIds\":[101,201,999]}"));

        assertThat(result.path("historicalSnapshotGuaranteed").asBoolean()).isFalse();
        assertThat(result.path("items")).singleElement().satisfies(item -> {
            assertThat(item.path("chunkId").asLong()).isEqualTo(101L);
            assertThat(item.path("chunkStatus").asText()).isEqualTo("ARCHIVED");
            assertThat(item.path("documentStatus").asText()).isEqualTo("ARCHIVED");
            assertThat(item.path("content").asText()).doesNotContain("secret");
        });
        assertThat(result.path("notFoundChunkIds")).extracting(JsonNode::asLong)
                .containsExactly(201L, 999L);
    }

    @Test
    void shouldExposeExactlyTheThreePhaseBReadOnlyTools() {
        assertThat(registry.definitions())
                .extracting(AgentToolDefinition::name)
                .containsExactlyInAnyOrder(
                        "searchKnowledge", "getChunkEvidence", "getAskLogEvidence");
    }

    @Test
    void shouldFlagLegacyAskLogAndBoundAnswerOutput() throws Exception {
        JsonNode result = askLogTool.execute(
                new AgentToolContext(7L, 1L),
                objectMapper.readTree("{\"askLogId\":301}"));

        assertThat(result.path("answerGroundingEvaluationEligible").asBoolean()).isFalse();
        assertThat(result.path("eligibilityNote").asText()).contains("must not be used");
        assertThat(result.path("retrievedChunkIds")).extracting(JsonNode::asLong)
                .containsExactly(101L);
        assertThat(result.path("answer").asText()).hasSize(4_000);
        assertThat(result.path("answerTruncated").asBoolean()).isTrue();

        assertThatThrownBy(() -> askLogTool.execute(
                new AgentToolContext(7L, 1L),
                objectMapper.readTree("{\"askLogId\":302}")))
                .isInstanceOf(BizException.class)
                .hasMessage("ask log not found");
    }
}
