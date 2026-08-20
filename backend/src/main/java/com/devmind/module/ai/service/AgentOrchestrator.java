package com.devmind.module.ai.service;

import com.devmind.common.api.ResultCode;
import com.devmind.common.exception.BizException;
import com.devmind.module.ai.agent.AgentBudgetLimits;
import com.devmind.module.ai.agent.AgentExperimentArm;
import com.devmind.module.ai.agent.AgentModelClient;
import com.devmind.module.ai.agent.AgentRunStatus;
import com.devmind.module.ai.agent.AgentToolContext;
import com.devmind.module.ai.agent.BadCaseStatus;
import com.devmind.module.ai.agent.EvidenceTriageInput;
import com.devmind.module.ai.agent.TriageDiagnosis;
import com.devmind.module.ai.agent.TriageProposalCandidate;
import com.devmind.module.ai.agent.TriageRoute;
import com.devmind.module.ai.entity.AgentRun;
import com.devmind.module.ai.entity.AiBadCase;
import com.devmind.module.ai.entity.RepairProposal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Objects;

@Service
public class AgentOrchestrator {

    private final BadCaseIntakeService badCaseIntakeService;
    private final AgentRunPersistenceService runPersistenceService;
    private final EvidenceTriageAgent triageAgent;
    private final ProposalValidator proposalValidator;
    private final RepairProposalService proposalService;
    private final TriageWorkflowPersistenceService workflowPersistenceService;
    private final ObjectMapper objectMapper;

    public AgentOrchestrator(BadCaseIntakeService badCaseIntakeService,
                             AgentRunPersistenceService runPersistenceService,
                             EvidenceTriageAgent triageAgent,
                             ProposalValidator proposalValidator,
                             RepairProposalService proposalService,
                             TriageWorkflowPersistenceService workflowPersistenceService,
                             ObjectMapper objectMapper) {
        this.badCaseIntakeService = badCaseIntakeService;
        this.runPersistenceService = runPersistenceService;
        this.triageAgent = triageAgent;
        this.proposalValidator = proposalValidator;
        this.proposalService = proposalService;
        this.workflowPersistenceService = workflowPersistenceService;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public TriageWorkflowResult triageAndRoute(Long userId,
                                               Long badCaseId,
                                               String idempotencyKey,
                                               AgentModelClient modelClient) {
        String safeKey = requireKey(idempotencyKey);
        Objects.requireNonNull(modelClient, "modelClient must not be null");
        AiBadCase candidate = badCaseIntakeService.getOwned(userId, badCaseId);
        AgentRun run = runPersistenceService.startRun(
                userId,
                badCaseId,
                AgentExperimentArm.REVIEWED_MULTI,
                AgentBudgetLimits.triageDefaults(),
                safeKey + ":triage");
        AgentRunStatus existingStatus = AgentRunStatus.valueOf(run.getStatus());
        if (existingStatus == AgentRunStatus.SUCCEEDED) {
            return existingResult(userId, safeKey, run);
        }
        if (existingStatus != AgentRunStatus.RUNNING) {
            throw new BizException(ResultCode.CONFLICT,
                    "triage workflow is already terminal: " + existingStatus);
        }
        if (!BadCaseStatus.NEW.name().equals(candidate.getStatus())) {
            throw new BizException(ResultCode.CONFLICT,
                    "bad case is not awaiting triage");
        }

        AskSnapshot snapshot = readSnapshot(candidate);
        if (!Objects.equals(candidate.getAskLogId(), snapshot.askLogId())) {
            failRun(userId, run.getId(), "BAD_CASE_SNAPSHOT_MISMATCH");
            throw new BizException(ResultCode.CONFLICT,
                    "bad-case snapshot does not match its ask log");
        }
        try {
            TriageDiagnosis diagnosis = triageAgent.triageForWorkflow(
                    new AgentToolContext(userId, run.getId()),
                    modelClient,
                    new EvidenceTriageInput(
                            snapshot.askLogId(), issueDescription(snapshot),
                            snapshot.expectedAnswer()));
            validateProposalCandidate(userId, badCaseId, diagnosis);
            return workflowPersistenceService.persist(
                    userId, badCaseId, run.getId(), safeKey + ":proposal", diagnosis);
        } catch (RuntimeException ex) {
            failRun(userId, run.getId(), "TRIAGE_WORKFLOW_FAILED");
            throw ex;
        }
    }

    private TriageWorkflowResult existingResult(Long userId,
                                                String key,
                                                AgentRun run) {
        AiBadCase badCase = badCaseIntakeService.getOwned(userId, run.getBadCaseId());
        RepairProposal proposal = proposalService.findOwnedByIdempotencyKey(
                userId, key + ":proposal");
        return new TriageWorkflowResult(run, badCase, proposal);
    }

    private void validateProposalCandidate(Long userId,
                                           Long badCaseId,
                                           TriageDiagnosis diagnosis) {
        if (diagnosis.recommendedRoute() != TriageRoute.RETRIEVAL_METADATA_PROPOSAL) {
            return;
        }
        TriageProposalCandidate candidate = diagnosis.proposal();
        if (candidate == null) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "retrieval-miss triage must include a metadata proposal");
        }
        proposalValidator.validate(
                userId,
                badCaseId,
                new RepairProposalDraft(
                        candidate.type(),
                        candidate.targetDocumentId(),
                        candidate.baseVersionNo(),
                        candidate.diffJson(),
                        candidate.evidenceJson(),
                        candidate.counterevidenceJson(),
                        candidate.impactJson(),
                        candidate.regressionPlanJson()));
    }

    private AskSnapshot readSnapshot(AiBadCase badCase) {
        try {
            return objectMapper.readValue(badCase.getAskSnapshotJson(), AskSnapshot.class);
        } catch (JsonProcessingException ex) {
            throw new BizException(ResultCode.INTERNAL_ERROR,
                    "bad-case ask snapshot is invalid");
        }
    }

    private String issueDescription(AskSnapshot snapshot) {
        if (StringUtils.hasText(snapshot.reason())) return snapshot.reason().trim();
        return "The saved answer was marked unhelpful for question: " + snapshot.question();
    }

    private void failRun(Long userId, Long runId, String code) {
        runPersistenceService.failRunIfActive(
                userId, runId, code, "triage workflow failed");
    }

    private String requireKey(String key) {
        if (!StringUtils.hasText(key) || key.trim().length() > 100) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "workflow idempotency key must be non-blank and at most 100 characters");
        }
        return key.trim();
    }
}
