package com.devmind.module.ai.service;

import com.devmind.common.exception.BizException;
import com.devmind.module.ai.agent.BadCaseStatus;
import com.devmind.module.ai.agent.RepairProposalStatus;
import com.devmind.module.ai.agent.RepairProposalType;
import com.devmind.module.ai.entity.AiBadCase;
import com.devmind.module.ai.entity.RepairProposal;
import com.devmind.module.ai.mapper.AiBadCaseMapper;
import com.devmind.module.document.entity.KnowledgeDocument;
import com.devmind.module.document.mapper.KnowledgeDocumentMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.url=jdbc:h2:mem:repair_proposal_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "devmind.ai.provider=mock"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RepairProposalIntegrationTest {

    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private AiBadCaseMapper badCaseMapper;
    @Autowired
    private KnowledgeDocumentMapper documentMapper;
    @Autowired
    private RepairProposalService proposalService;

    private Long badCaseId;

    @BeforeAll
    void createSchemaAndFixtures() {
        jdbcTemplate.execute("""
                CREATE TABLE user_account (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    username VARCHAR(64) NOT NULL UNIQUE,
                    password_hash VARCHAR(255) NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE knowledge_document (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
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
        jdbcTemplate.execute("""
                CREATE TABLE agent_run (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    bad_case_id BIGINT
                )
                """);
        jdbcTemplate.update("""
                INSERT INTO user_account (id, username, password_hash)
                VALUES (7, 'owner', 'hash'), (8, 'other', 'hash')
                """);
        jdbcTemplate.update("""
                INSERT INTO knowledge_document
                    (id, user_id, title, content, source_type, tags, summary, status)
                VALUES (31, 7, 'Spring transactions',
                        'Transaction propagation controls nested service boundaries.',
                        'java_note', 'spring,transaction', 'Transaction reference', 1),
                       (32, 8, 'Private note', 'other tenant evidence',
                        'java_note', 'private', 'private', 1),
                       (33, 7, 'Stale candidate',
                        'Transaction propagation is documented for the stale test.',
                        'java_note', 'spring,transaction', 'Stale test', 1)
                """);

        applyMigration("db/migration/V9__version_knowledge_documents.sql");
        applyMigration("db/migration/V10__create_bad_case_intake.sql");
        applyMigration("db/migration/V11__create_repair_proposal.sql");

        AiBadCase badCase = new AiBadCase();
        badCase.setUserId(7L);
        badCase.setSourceType("EVALUATION");
        badCase.setSourceRef("sealed:metadata-1");
        badCase.setAskSnapshotJson("{}");
        badCase.setChunkSnapshotJson("[]");
        badCase.setPromptSchemaVersion(PromptSchemaVersions.CURRENT);
        badCase.setRootCause("knowledge_exists_not_retrieved");
        badCase.setDiagnosisJson("{}");
        badCase.setStatus(BadCaseStatus.TRIAGED.name());
        badCase.setStatusVersion(0);
        badCaseMapper.insert(badCase);
        badCaseId = badCase.getId();
    }

    @Test
    void shouldValidatePersistAndDeduplicateSafeMetadataProposal() {
        RepairProposalDraft draft = metadataDraft(31L, 1, "spring,transaction,propagation");

        RepairProposal created = proposalService.create(7L, badCaseId, "proposal:metadata-1", draft);
        RepairProposal retried = proposalService.create(7L, badCaseId, "proposal:metadata-1", draft);

        assertThat(retried.getId()).isEqualTo(created.getId());
        assertThat(created.getProposalType()).isEqualTo(RepairProposalType.METADATA_PATCH.name());
        assertThat(created.getStatus()).isEqualTo(RepairProposalStatus.DRAFT.name());
        assertThat(created.getRevisionNo()).isZero();
        assertThat(created.getLockVersion()).isZero();
        assertThat(created.getDiffJson()).contains("propagation");
        assertThat(badCaseMapper.selectById(badCaseId).getStatus())
                .isEqualTo(BadCaseStatus.PROPOSED.name());

        assertThatThrownBy(() -> proposalService.create(
                7L, badCaseId, "proposal:metadata-1",
                metadataDraft(31L, 1, "different")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("reused with different input");
    }

    @Test
    void shouldRejectCrossTenantEvidenceAndStaleBaseVersion() {
        AiBadCase crossTenantCase = triagedCase("sealed:metadata-cross-tenant");
        RepairProposalDraft crossTenant = new RepairProposalDraft(
                RepairProposalType.METADATA_PATCH,
                31L,
                1,
                "{\"tags\":\"spring,transaction,propagation\"}",
                "[{\"kind\":\"DOCUMENT_VERSION\",\"documentId\":32,"
                        + "\"documentVersionNo\":1,\"excerpt\":\"other tenant evidence\","
                        + "\"claim\":\"cross tenant\"}]",
                "[]",
                impactJson(),
                regressionJson());
        assertThatThrownBy(() -> proposalService.create(
                7L, crossTenantCase.getId(), "proposal:cross-tenant", crossTenant))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("document version not found");

        AiBadCase staleCase = triagedCase("sealed:metadata-stale");
        KnowledgeDocument document = documentMapper.selectById(33L);
        document.setTags("spring,transaction,updated");
        assertThat(documentMapper.updateById(document)).isEqualTo(1);
        assertThat(document.getVersionNo()).isEqualTo(2);
        assertThatThrownBy(() -> proposalService.create(
                7L, staleCase.getId(), "proposal:stale",
                metadataDraft(33L, 1, "spring,transaction,stale")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("base version is stale");
    }

    @Test
    void shouldAllowNonExecutableDocumentDraftOnlyWithUserTrustedSource() {
        AiBadCase missingCase = triagedCase("sealed:missing-with-source");
        missingCase.setRootCause("knowledge_missing");
        missingCase.setTrustedSourceJson("[{\"sourceId\":\"user-source-1\","
                + "\"title\":\"Official team note\","
                + "\"content\":\"The retry policy uses exponential backoff.\","
                + "\"origin\":\"USER_SUPPLIED\"}]");
        assertThat(badCaseMapper.updateById(missingCase)).isEqualTo(1);
        RepairProposalDraft draft = new RepairProposalDraft(
                RepairProposalType.DOCUMENT_DRAFT,
                null,
                null,
                "{\"title\":\"Retry policy\","
                        + "\"content\":\"The retry policy uses exponential backoff.\","
                        + "\"sourceType\":\"project_doc\",\"tags\":\"retry,backoff\","
                        + "\"summary\":\"Team retry policy\"}",
                "[{\"kind\":\"TRUSTED_USER_SOURCE\","
                        + "\"trustedSourceId\":\"user-source-1\","
                        + "\"excerpt\":\"exponential backoff\","
                        + "\"claim\":\"The supplied source defines retry behavior.\"}]",
                "[]",
                "{\"summary\":\"Creates a review-only draft.\",\"risk\":\"MEDIUM\","
                        + "\"affectedQueries\":[\"retry policy\"]}",
                "{\"targetQuestion\":\"What is the retry policy?\","
                        + "\"relatedKeywords\":[\"retry\"],"
                        + "\"fullDatasetVersion\":\"rag-v1\"}");

        RepairProposal proposal = proposalService.create(
                7L, missingCase.getId(), "proposal:trusted-draft", draft);

        assertThat(proposal.getProposalType()).isEqualTo(RepairProposalType.DOCUMENT_DRAFT.name());
        assertThat(RepairProposalType.valueOf(proposal.getProposalType()).isExecutable()).isFalse();
    }

    private AiBadCase triagedCase(String sourceRef) {
        AiBadCase badCase = new AiBadCase();
        badCase.setUserId(7L);
        badCase.setSourceType("EVALUATION");
        badCase.setSourceRef(sourceRef);
        badCase.setAskSnapshotJson("{}");
        badCase.setChunkSnapshotJson("[]");
        badCase.setPromptSchemaVersion(PromptSchemaVersions.CURRENT);
        badCase.setRootCause("knowledge_exists_not_retrieved");
        badCase.setDiagnosisJson("{}");
        badCase.setStatus(BadCaseStatus.TRIAGED.name());
        badCase.setStatusVersion(0);
        badCaseMapper.insert(badCase);
        return badCase;
    }

    private RepairProposalDraft metadataDraft(Long documentId, int baseVersion, String tags) {
        return new RepairProposalDraft(
                RepairProposalType.METADATA_PATCH,
                documentId,
                baseVersion,
                "{\"tags\":\"" + tags + "\"}",
                "[{\"kind\":\"DOCUMENT_VERSION\",\"documentId\":31,"
                        + "\"documentVersionNo\":1,"
                        + "\"excerpt\":\"Transaction propagation\","
                        + "\"claim\":\"The source covers propagation.\"}]",
                "[]",
                impactJson(),
                regressionJson());
    }

    private String impactJson() {
        return "{\"summary\":\"Improves metadata recall.\",\"risk\":\"LOW\","
                + "\"affectedQueries\":[\"transaction propagation\"]}";
    }

    private String regressionJson() {
        return "{\"targetQuestion\":\"How does propagation work?\","
                + "\"relatedKeywords\":[\"propagation\"],"
                + "\"fullDatasetVersion\":\"rag-v1\"}";
    }

    private void applyMigration(String resource) {
        new ResourceDatabasePopulator(new ClassPathResource(resource)).execute(dataSource);
    }
}
