package com.devmind.module.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devmind.common.exception.BizException;
import com.devmind.module.ai.agent.BadCaseStatus;
import com.devmind.module.ai.agent.TriageDiagnosis;
import com.devmind.module.ai.agent.TriageEvidence;
import com.devmind.module.ai.agent.TriageRootCause;
import com.devmind.module.ai.dto.AskFeedbackRequest;
import com.devmind.module.ai.entity.AiBadCase;
import com.devmind.module.ai.mapper.AiBadCaseMapper;
import com.devmind.module.search.vo.ChunkSearchResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.url=jdbc:h2:mem:bad_case_intake_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "devmind.ai.provider=mock"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BadCaseIntakeIntegrationTest {

    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private AiAskLogService askLogService;
    @Autowired
    private AiAskFeedbackService feedbackService;
    @Autowired
    private BadCaseStateService stateService;
    @Autowired
    private AiBadCaseMapper badCaseMapper;
    @Autowired
    private ObjectMapper objectMapper;

    @BeforeAll
    void createSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE user_account (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    username VARCHAR(64) NOT NULL UNIQUE,
                    password_hash VARCHAR(255) NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE ai_ask_log (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    user_id BIGINT NOT NULL,
                    question VARCHAR(500) NOT NULL,
                    retrieval_keyword VARCHAR(128) NOT NULL,
                    prompt_preview CLOB,
                    prompt_schema_version TINYINT,
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
        jdbcTemplate.execute("""
                CREATE TABLE ai_ask_feedback (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    user_id BIGINT NOT NULL,
                    ask_log_id BIGINT NOT NULL,
                    helpful TINYINT NOT NULL,
                    reason VARCHAR(500),
                    expected_answer CLOB,
                    status TINYINT NOT NULL,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
                """);
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/V10__create_bad_case_intake.sql"));
        populator.execute(dataSource);
        jdbcTemplate.update("""
                INSERT INTO user_account (id, username, password_hash)
                VALUES (7, 'owner', 'hash'), (8, 'other', 'hash')
                """);
    }

    @Test
    void shouldSnapshotVisibleEvidenceDeduplicateAndEnforceStateMachine() throws Exception {
        String longContent = "evidence-" + "x".repeat(800);
        ChunkSearchResponse visibleChunk = new ChunkSearchResponse(
                101L, 31L, "Versioned note", "java_note", "tx,spring",
                0, longContent, 200, 88, 3);
        Long askLogId = askLogService.saveSuccessLog(
                7L,
                "How do transactions propagate?",
                "transactions,propagate",
                "bounded preview",
                "Original answer",
                "mock",
                true,
                100,
                20,
                120,
                List.of(visibleChunk),
                45L);

        AskFeedbackRequest request = negativeFeedback();
        feedbackService.saveFeedback(7L, askLogId, request);
        feedbackService.saveFeedback(7L, askLogId, request);

        List<AiBadCase> badCases = badCaseMapper.selectList(
                new LambdaQueryWrapper<AiBadCase>()
                        .eq(AiBadCase::getUserId, 7L));
        assertThat(badCases).hasSize(1);
        AiBadCase badCase = badCases.get(0);
        assertThat(badCase.getStatus()).isEqualTo(BadCaseStatus.NEW.name());
        assertThat(badCase.getStatusVersion()).isZero();
        assertThat(badCase.getPromptSchemaVersion()).isEqualTo(PromptSchemaVersions.CURRENT);

        JsonNode askSnapshot = objectMapper.readTree(badCase.getAskSnapshotJson());
        assertThat(askSnapshot.path("askLogId").asLong()).isEqualTo(askLogId);
        assertThat(askSnapshot.path("expectedAnswer").asText()).isEqualTo("Expected answer clue");
        JsonNode chunkSnapshot = objectMapper.readTree(badCase.getChunkSnapshotJson()).get(0);
        assertThat(chunkSnapshot.path("documentVersionNo").asInt()).isEqualTo(3);
        assertThat(chunkSnapshot.path("modelVisibleContent").asText())
                .hasSize(603)
                .endsWith("...");

        AiBadCase stale = badCaseMapper.selectById(badCase.getId());
        TriageDiagnosis diagnosis = new TriageDiagnosis(
                TriageRootCause.KNOWLEDGE_MISSING,
                "No owned source supports the requested claim.",
                List.of(new TriageEvidence("tool-1", askLogId, null, "No context was found.")),
                TriageRootCause.KNOWLEDGE_MISSING.requiredRoute(),
                0.85);
        AiBadCase triaged = stateService.recordTriage(7L, badCase.getId(), diagnosis);
        assertThat(triaged.getStatus()).isEqualTo(BadCaseStatus.TRIAGED.name());
        assertThat(triaged.getRootCause()).isEqualTo("knowledge_missing");
        assertThat(triaged.getStatusVersion()).isEqualTo(1);

        AiBadCase ticketed = stateService.transition(
                7L, badCase.getId(), BadCaseStatus.TRIAGED, BadCaseStatus.TICKETED);
        assertThat(ticketed.getStatus()).isEqualTo(BadCaseStatus.TICKETED.name());
        assertThat(ticketed.getStatusVersion()).isEqualTo(2);

        stale.setStatus(BadCaseStatus.FAILED.name());
        assertThat(badCaseMapper.updateById(stale)).isZero();
        assertThatThrownBy(() -> stateService.transition(
                7L, badCase.getId(), BadCaseStatus.TICKETED, BadCaseStatus.PROPOSED))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("invalid bad case transition");
        assertThatThrownBy(() -> stateService.transition(
                8L, badCase.getId(), BadCaseStatus.TICKETED, BadCaseStatus.FAILED))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void helpfulFeedbackShouldNotCreateBadCase() {
        Long askLogId = askLogService.saveSuccessLog(
                7L, "Question", "keyword", "preview", "answer", "mock", true,
                null, null, null, List.of(), 5L);
        AskFeedbackRequest request = new AskFeedbackRequest();
        request.setHelpful(true);

        feedbackService.saveFeedback(7L, askLogId, request);

        assertThat(badCaseMapper.selectCount(new LambdaQueryWrapper<AiBadCase>()
                .eq(AiBadCase::getAskLogId, askLogId))).isZero();
    }

    private AskFeedbackRequest negativeFeedback() {
        AskFeedbackRequest request = new AskFeedbackRequest();
        request.setHelpful(false);
        request.setReason("The answer missed transaction propagation.");
        request.setExpectedAnswer("Expected answer clue");
        return request;
    }
}
