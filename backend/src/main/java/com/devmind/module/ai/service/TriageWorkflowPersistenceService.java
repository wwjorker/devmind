package com.devmind.module.ai.service;

import com.devmind.common.api.ResultCode;
import com.devmind.common.exception.BizException;
import com.devmind.module.ai.agent.AgentRunStatus;
import com.devmind.module.ai.agent.BadCaseStatus;
import com.devmind.module.ai.agent.TriageDiagnosis;
import com.devmind.module.ai.agent.TriageProposalCandidate;
import com.devmind.module.ai.agent.TriageRoute;
import com.devmind.module.ai.entity.AgentRun;
import com.devmind.module.ai.entity.AiBadCase;
import com.devmind.module.ai.entity.RepairProposal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TriageWorkflowPersistenceService {

    private final BadCaseStateService badCaseStateService;
    private final RepairProposalService proposalService;
    private final AgentRunPersistenceService runPersistenceService;

    public TriageWorkflowPersistenceService(BadCaseStateService badCaseStateService,
                                            RepairProposalService proposalService,
                                            AgentRunPersistenceService runPersistenceService) {
        this.badCaseStateService = badCaseStateService;
        this.proposalService = proposalService;
        this.runPersistenceService = runPersistenceService;
    }

    @Transactional
    public TriageWorkflowResult persist(Long userId,
                                        Long badCaseId,
                                        Long runId,
                                        String proposalKey,
                                        TriageDiagnosis diagnosis) {
        AiBadCase triaged = badCaseStateService.recordTriage(userId, badCaseId, diagnosis);
        RepairProposal proposal = route(userId, triaged, diagnosis, proposalKey);
        AgentRunStatus status = runPersistenceService.markSucceededInCurrentTransaction(
                userId,
                runId,
                "rootCause=" + diagnosis.rootCause().name()
                        + ";route=" + diagnosis.recommendedRoute().name()
                        + ";proposalId=" + (proposal == null ? "none" : proposal.getId()));
        if (status != AgentRunStatus.SUCCEEDED) {
            throw new BizException(ResultCode.CONFLICT,
                    "triage run could not be completed: " + status);
        }
        AgentRun completed = runPersistenceService.getOwnedRun(userId, runId);
        return new TriageWorkflowResult(completed, triaged, proposal);
    }

    private RepairProposal route(Long userId,
                                 AiBadCase badCase,
                                 TriageDiagnosis diagnosis,
                                 String proposalKey) {
        TriageRoute route = diagnosis.recommendedRoute();
        if (route == TriageRoute.RETRIEVAL_METADATA_PROPOSAL) {
            TriageProposalCandidate candidate = diagnosis.proposal();
            if (candidate == null) {
                throw new BizException(ResultCode.BAD_REQUEST,
                        "retrieval-miss triage must include a metadata proposal");
            }
            return proposalService.create(
                    userId,
                    badCase.getId(),
                    proposalKey,
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
        BadCaseStatus target = switch (route) {
            case KNOWLEDGE_GAP_TICKET -> BadCaseStatus.TICKETED;
            case HUMAN_SOURCE_CONFLICT_REVIEW -> BadCaseStatus.CONFLICT_PENDING;
            case ANSWER_POLICY_REVIEW, EXPECTED_ANSWER_CORRECTION,
                    NO_ACTION_OUT_OF_SCOPE -> BadCaseStatus.NO_ACTION;
            case RETRIEVAL_METADATA_PROPOSAL -> throw new IllegalStateException();
        };
        badCaseStateService.transition(
                userId, badCase.getId(), BadCaseStatus.TRIAGED, target);
        return null;
    }
}
