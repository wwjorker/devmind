package com.devmind.module.ai.service;

import com.devmind.common.exception.BizException;
import com.devmind.module.ai.agent.BadCaseStatus;
import com.devmind.module.ai.agent.ApprovalDecision;
import com.devmind.module.ai.agent.RepairProposalStatus;
import com.devmind.module.ai.agent.RepairProposalType;
import com.devmind.module.ai.agent.ReviewerDecision;
import com.devmind.module.ai.agent.ReviewerFinding;
import com.devmind.module.ai.agent.ReviewerSeverity;
import com.devmind.module.ai.agent.ReviewerVerdict;
import com.devmind.module.ai.entity.AiBadCase;
import com.devmind.module.ai.entity.RepairProposal;
import com.devmind.module.ai.mapper.AiBadCaseMapper;
import com.devmind.module.document.entity.KnowledgeDocument;
import com.devmind.module.document.mapper.KnowledgeDocumentMapper;
import com.devmind.module.document.service.KnowledgeDocumentVersionService;
import com.devmind.module.search.service.ChunkVectorService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    @Autowired
    private ProposalReviewPersistenceService reviewPersistenceService;
    @Autowired
    private ProposalApprovalService approvalService;
    @Autowired
    private RepairExecutor repairExecutor;
    @Autowired
    private RepairExecutionStateService executionStateService;
    @Autowired
    private RepairDocumentMutationService mutationService;
    @Autowired
    private RecoveryService recoveryService;
    @Autowired
    private KnowledgeDocumentVersionService versionService;

    @MockBean
    private ChunkVectorService vectorService;
    @MockBean
    private RegressionRunner regressionRunner;

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
                CREATE TABLE knowledge_document_chunk (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
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
                        'java_note', 'spring,transaction', 'Stale test', 1),
                       (34, 7, 'Executor candidate',
                        'Retry backoff is documented for the executor test.',
                        'java_note', 'retry,policy', 'Executor test', 1),
                       (35, 7, 'Rollback candidate',
                        'Retry backoff is documented for the rollback test.',
                        'java_note', 'retry,policy', 'Rollback test', 1),
                       (36, 7, 'Recovery candidate',
                        'Retry backoff is documented for the recovery test.',
                        'java_note', 'retry,policy', 'Recovery test', 1),
                       (37, 7, 'Rollback recovery candidate',
                        'Retry backoff is documented for rollback recovery.',
                        'java_note', 'retry,policy', 'Rollback recovery test', 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO knowledge_document_chunk
                    (id, document_id, user_id, chunk_index, content, token_count, status)
                VALUES (301, 31, 7, 0, 'Transaction propagation controls boundaries.', 20, 1),
                       (303, 33, 7, 0, 'Transaction propagation stale fixture.', 20, 1),
                       (304, 34, 7, 0, 'Retry backoff executor fixture.', 20, 1),
                       (305, 35, 7, 0, 'Retry backoff rollback fixture.', 20, 1),
                       (306, 36, 7, 0, 'Retry backoff recovery fixture.', 20, 1),
                       (307, 37, 7, 0, 'Retry backoff rollback recovery fixture.', 20, 1)
                """);

        applyMigration("db/migration/V9__version_knowledge_documents.sql");
        applyMigration("db/migration/V10__create_bad_case_intake.sql");
        applyMigration("db/migration/V11__create_repair_proposal.sql");
        applyMigration("db/migration/V12__add_proposal_revision_idempotency.sql");
        applyMigration("db/migration/V13__add_proposal_approval_decision.sql");

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

    @Test
    void shouldAllowExactlyOneReviewedRevision() {
        AiBadCase revisionCase = triagedCase("sealed:metadata-revision");
        RepairProposal original = proposalService.create(
                7L, revisionCase.getId(), "proposal:revision", metadataDraft(
                        31L, 1, "spring,transaction,first"));
        ReviewerDecision revise = blockingDecision(ReviewerVerdict.REVISE);

        RepairProposal reviewed = reviewPersistenceService.saveDecision(
                7L, original.getId(), revise);
        assertThat(reviewed.getStatus()).isEqualTo(RepairProposalStatus.REVIEWED.name());
        assertThat(badCaseMapper.selectById(revisionCase.getId()).getStatus())
                .isEqualTo(BadCaseStatus.REVIEWED.name());

        RepairProposalDraft revisedDraft = metadataDraft(
                31L, 1, "spring,transaction,revised");
        RepairProposal revised = proposalService.revise(
                7L, original.getId(), "proposal:revision:1", revisedDraft);
        RepairProposal retried = proposalService.revise(
                7L, original.getId(), "proposal:revision:1", revisedDraft);
        assertThat(retried.getId()).isEqualTo(revised.getId());
        assertThat(revised.getRevisionNo()).isEqualTo(1);
        assertThat(revised.getStatus()).isEqualTo(RepairProposalStatus.DRAFT.name());
        assertThat(badCaseMapper.selectById(revisionCase.getId()).getStatus())
                .isEqualTo(BadCaseStatus.PROPOSED.name());

        RepairProposal rejected = reviewPersistenceService.saveDecision(
                7L, original.getId(), revise);
        assertThat(rejected.getReviewerVerdict()).isEqualTo(ReviewerVerdict.REVISE.name());
        assertThat(rejected.getStatus()).isEqualTo(RepairProposalStatus.REJECTED.name());
        assertThat(badCaseMapper.selectById(revisionCase.getId()).getStatus())
                .isEqualTo(BadCaseStatus.NO_ACTION.name());
    }

    @Test
    void reviewerPassShouldAdvanceProposalToHumanApproval() {
        AiBadCase passCase = triagedCase("sealed:metadata-pass");
        RepairProposal proposal = proposalService.create(
                7L, passCase.getId(), "proposal:pass",
                metadataDraft(31L, 1, "spring,transaction,approved-candidate"));
        ReviewerDecision pass = new ReviewerDecision(
                ReviewerVerdict.PASS,
                "The proposal is supported and remains within metadata scope.",
                java.util.List.of(),
                0.9);

        RepairProposal reviewed = reviewPersistenceService.saveDecision(
                7L, proposal.getId(), pass);

        assertThat(reviewed.getStatus()).isEqualTo(RepairProposalStatus.AWAITING_APPROVAL.name());
        assertThat(badCaseMapper.selectById(passCase.getId()).getStatus())
                .isEqualTo(BadCaseStatus.AWAITING_APPROVAL.name());
    }

    @Test
    void shouldApproveEditedDiffIdempotently() {
        AiBadCase approvalCase = triagedCase("sealed:metadata-approval");
        RepairProposal proposal = awaitingProposal(
                approvalCase, "proposal:approval", "spring,transaction,candidate");
        ProposalApprovalCommand command = new ProposalApprovalCommand(
                ApprovalDecision.APPROVE_WITH_EDIT,
                "approval:metadata-1",
                "{\"tags\":\"spring,transaction,human-approved\"}",
                "Narrowed the tags before execution.");

        RepairProposal approved = approvalService.decide(7L, proposal.getId(), command);
        RepairProposal retried = approvalService.decide(7L, proposal.getId(), command);

        assertThat(retried.getId()).isEqualTo(approved.getId());
        assertThat(approved.getStatus()).isEqualTo(RepairProposalStatus.APPROVED.name());
        assertThat(approved.getApprovalDecision())
                .isEqualTo(ApprovalDecision.APPROVE_WITH_EDIT.name());
        assertThat(approved.getApprovedDiffJson()).contains("human-approved");
        assertThat(badCaseMapper.selectById(approvalCase.getId()).getStatus())
                .isEqualTo(BadCaseStatus.APPROVED.name());

        ProposalApprovalCommand changedRetry = new ProposalApprovalCommand(
                ApprovalDecision.APPROVE_WITH_EDIT,
                "approval:metadata-1",
                "{\"tags\":\"different\"}",
                "Narrowed the tags before execution.");
        assertThatThrownBy(() -> approvalService.decide(
                7L, proposal.getId(), changedRetry))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("reused with different input");
    }

    @Test
    void shouldRecordHumanRejectionWithoutExecuting() {
        AiBadCase rejectionCase = triagedCase("sealed:metadata-rejection");
        RepairProposal proposal = awaitingProposal(
                rejectionCase, "proposal:rejection", "spring,transaction,reject");

        RepairProposal rejected = approvalService.decide(
                7L,
                proposal.getId(),
                new ProposalApprovalCommand(
                        ApprovalDecision.REJECT,
                        "approval:reject-1",
                        null,
                        "The change is not appropriate for this knowledge base."));

        assertThat(rejected.getStatus()).isEqualTo(RepairProposalStatus.REJECTED.name());
        assertThat(rejected.getApprovedDiffJson()).isNull();
        assertThat(badCaseMapper.selectById(rejectionCase.getId()).getStatus())
                .isEqualTo(BadCaseStatus.NO_ACTION.name());
    }

    @Test
    void shouldExecuteApprovedMetadataIdempotentlyAndResolve() {
        reset(vectorService, regressionRunner);
        AiBadCase executionCase = triagedCase("sealed:metadata-execution-success");
        RepairProposal approved = approvedProposal(
                executionCase,
                34L,
                "proposal:execution-success",
                "approval:execution-success",
                "retry,policy,backoff");
        when(regressionRunner.runTargetRetrieval(eq(7L), any()))
                .thenAnswer(invocation -> {
                    assertThat(org.springframework.transaction.support
                            .TransactionSynchronizationManager.isActualTransactionActive())
                            .isFalse();
                    return new RegressionResult(
                            true, "How does retry backoff work?", 34L, 1,
                            java.util.List.of(304L), "target document retrieved");
                });
        doAnswer(invocation -> {
            assertThat(org.springframework.transaction.support
                    .TransactionSynchronizationManager.isActualTransactionActive())
                    .isFalse();
            return null;
        }).when(vectorService).rebuildVectors(eq(7L), eq(34L), any());

        RepairProposal applied = repairExecutor.execute(
                7L, approved.getId(), "execution:success");
        RepairProposal retried = repairExecutor.execute(
                7L, approved.getId(), "execution:success");

        assertThat(retried.getId()).isEqualTo(applied.getId());
        assertThat(applied.getStatus()).isEqualTo(RepairProposalStatus.APPLIED.name());
        KnowledgeDocument document = documentMapper.selectById(34L);
        assertThat(document.getTags()).isEqualTo("retry,policy,backoff");
        assertThat(document.getVersionNo()).isEqualTo(2);
        assertThat(versionService.getOwnedVersion(7L, 34L, 2).getProposalId())
                .isEqualTo(approved.getId());
        assertThat(badCaseMapper.selectById(executionCase.getId()).getStatus())
                .isEqualTo(BadCaseStatus.RESOLVED.name());
        verify(vectorService).rebuildVectors(eq(7L), eq(34L), any());
    }

    @Test
    void shouldResumeAStaleReservedExecutionWithoutDuplicatingTheWrite() {
        reset(vectorService, regressionRunner);
        AiBadCase recoveryCase = triagedCase("sealed:metadata-execution-recovery");
        RepairProposal approved = approvedProposal(
                recoveryCase,
                36L,
                "proposal:execution-recovery",
                "approval:execution-recovery",
                "retry,policy,recovered");
        executionStateService.reserve(7L, approved.getId(), "execution:recovery");
        jdbcTemplate.update(
                "UPDATE repair_proposal SET updated_at = DATEADD('HOUR', -2, CURRENT_TIMESTAMP) WHERE id = ?",
                approved.getId());
        when(regressionRunner.runTargetRetrieval(eq(7L), any()))
                .thenReturn(new RegressionResult(
                        true, "How does retry backoff work?", 36L, 1,
                        java.util.List.of(306L), "target document retrieved"));

        java.util.List<RepairProposal> recovered = recoveryService.recoverStale(
                7L, Duration.ofMinutes(30));

        assertThat(recovered).extracting(RepairProposal::getId)
                .contains(approved.getId());
        RepairProposal result = proposalService.getOwned(7L, approved.getId());
        assertThat(result.getStatus()).isEqualTo(RepairProposalStatus.APPLIED.name());
        assertThat(documentMapper.selectById(36L).getVersionNo()).isEqualTo(2);
        verify(vectorService).rebuildVectors(eq(7L), eq(36L), any());
    }

    @Test
    void shouldFinishACompensationThatCrashedAfterTheRollbackWrite() {
        reset(vectorService, regressionRunner);
        AiBadCase recoveryCase = triagedCase("sealed:metadata-rollback-recovery");
        RepairProposal approved = approvedProposal(
                recoveryCase,
                37L,
                "proposal:rollback-recovery",
                "approval:rollback-recovery",
                "retry,policy,temporary");
        String executionKey = "execution:rollback-recovery";
        RepairProposal executing = executionStateService.reserve(
                7L, approved.getId(), executionKey);
        MetadataMutationResult applied = mutationService.applyApprovedMetadata(
                7L, executing.getId(), executionKey);
        executionStateService.markVerifying(
                7L, executing.getId(), executionKey, applied.versionNo());
        mutationService.rollbackMetadata(
                7L, executing.getId(), executionKey, applied.versionNo());
        jdbcTemplate.update(
                "UPDATE repair_proposal SET updated_at = DATEADD('HOUR', -2, CURRENT_TIMESTAMP) WHERE id = ?",
                approved.getId());
        doThrow(new IllegalStateException("force compensation resume"))
                .when(vectorService).rebuildVectors(eq(7L), eq(37L), any());

        java.util.List<RepairProposal> recovered = recoveryService.recoverStale(
                7L, Duration.ofMinutes(30));

        assertThat(recovered).extracting(RepairProposal::getId)
                .contains(approved.getId());
        RepairProposal result = proposalService.getOwned(7L, approved.getId());
        assertThat(result.getStatus()).isEqualTo(RepairProposalStatus.ROLLED_BACK.name());
        assertThat(documentMapper.selectById(37L).getVersionNo()).isEqualTo(3);
        assertThat(versionService.getOwnedVersion(7L, 37L, 3).getOrigin())
                .isEqualTo("REPAIR_ROLLBACK");
    }

    @Test
    void shouldRollbackDocumentWhenIndexRebuildFails() {
        reset(vectorService, regressionRunner);
        AiBadCase rollbackCase = triagedCase("sealed:metadata-execution-rollback");
        RepairProposal approved = approvedProposal(
                rollbackCase,
                35L,
                "proposal:execution-rollback",
                "approval:execution-rollback",
                "retry,policy,temporary");
        java.util.concurrent.atomic.AtomicInteger rebuildCalls =
                new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(invocation -> {
            int call = rebuildCalls.getAndIncrement();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT version_no FROM knowledge_document WHERE id = 35",
                    Integer.class)).isEqualTo(call == 0 ? 2 : 3);
            if (call == 0) {
                throw new IllegalStateException("embedding unavailable");
            }
            return null;
        }).when(vectorService).rebuildVectors(eq(7L), eq(35L), any());

        RepairProposal rolledBack = repairExecutor.execute(
                7L, approved.getId(), "execution:rollback");

        assertThat(rolledBack.getStatus()).isEqualTo(RepairProposalStatus.ROLLED_BACK.name());
        assertThat(rolledBack.getExecutionResultJson())
                .contains("ROLLED_BACK")
                .contains("\"indexRecoveryRequired\":false");
        KnowledgeDocument document = documentMapper.selectById(35L);
        assertThat(document.getTags()).isEqualTo("retry,policy");
        assertThat(document.getVersionNo()).isEqualTo(3);
        assertThat(versionService.getOwnedVersion(7L, 35L, 3).getOrigin())
                .isEqualTo("REPAIR_ROLLBACK");
        assertThat(badCaseMapper.selectById(rollbackCase.getId()).getStatus())
                .isEqualTo(BadCaseStatus.ROLLED_BACK.name());
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

    private ReviewerDecision blockingDecision(ReviewerVerdict verdict) {
        return new ReviewerDecision(
                verdict,
                "The proposal must address a blocking evidence gap.",
                java.util.List.of(new ReviewerFinding(
                        "MISSING_COUNTEREVIDENCE",
                        ReviewerSeverity.BLOCKING,
                        "The proposal omitted a current source.",
                        "counterevidence",
                        null,
                        null)),
                0.85);
    }

    private RepairProposal awaitingProposal(AiBadCase badCase,
                                            String key,
                                            String tags) {
        RepairProposal proposal = proposalService.create(
                7L, badCase.getId(), key, metadataDraft(31L, 1, tags));
        return reviewPersistenceService.saveDecision(
                7L,
                proposal.getId(),
                new ReviewerDecision(
                        ReviewerVerdict.PASS,
                        "The proposal is supported and remains within metadata scope.",
                        java.util.List.of(),
                        0.9));
    }

    private RepairProposal approvedProposal(AiBadCase badCase,
                                            Long documentId,
                                            String proposalKey,
                                            String approvalKey,
                                            String tags) {
        int baseVersion = documentMapper.selectById(documentId).getVersionNo();
        RepairProposal proposal = proposalService.create(
                7L,
                badCase.getId(),
                proposalKey,
                metadataDraftFor(documentId, baseVersion, tags, "Retry backoff"));
        RepairProposal reviewed = reviewPersistenceService.saveDecision(
                7L,
                proposal.getId(),
                new ReviewerDecision(
                        ReviewerVerdict.PASS,
                        "The proposal is supported and remains within metadata scope.",
                        java.util.List.of(),
                        0.9));
        return approvalService.decide(
                7L,
                reviewed.getId(),
                new ProposalApprovalCommand(
                        ApprovalDecision.APPROVE,
                        approvalKey,
                        null,
                        "Approved for controlled execution."));
    }

    private RepairProposalDraft metadataDraftFor(Long documentId,
                                                  int baseVersion,
                                                  String tags,
                                                  String excerpt) {
        return new RepairProposalDraft(
                RepairProposalType.METADATA_PATCH,
                documentId,
                baseVersion,
                "{\"tags\":\"" + tags + "\"}",
                "[{\"kind\":\"DOCUMENT_VERSION\",\"documentId\":" + documentId + ","
                        + "\"documentVersionNo\":" + baseVersion + ","
                        + "\"excerpt\":\"" + excerpt + "\","
                        + "\"claim\":\"The source supports the metadata patch.\"}]",
                "[]",
                "{\"summary\":\"Improves controlled retrieval.\",\"risk\":\"LOW\","
                        + "\"affectedQueries\":[\"retry backoff\"]}",
                "{\"targetQuestion\":\"How does retry backoff work?\","
                        + "\"relatedKeywords\":[\"retry\",\"backoff\"],"
                        + "\"fullDatasetVersion\":\"rag-v1\"}");
    }

    private void applyMigration(String resource) {
        new ResourceDatabasePopulator(new ClassPathResource(resource)).execute(dataSource);
    }
}
