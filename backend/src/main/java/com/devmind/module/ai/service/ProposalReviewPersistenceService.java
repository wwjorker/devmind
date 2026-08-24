package com.devmind.module.ai.service;

import com.devmind.common.api.ResultCode;
import com.devmind.common.exception.BizException;
import com.devmind.module.ai.agent.BadCaseStatus;
import com.devmind.module.ai.agent.AgentRunStatus;
import com.devmind.module.ai.agent.RepairProposalStatus;
import com.devmind.module.ai.agent.ReviewerDecision;
import com.devmind.module.ai.agent.ReviewerVerdict;
import com.devmind.module.ai.entity.RepairProposal;
import com.devmind.module.ai.mapper.RepairProposalMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProposalReviewPersistenceService {

    private final RepairProposalMapper proposalMapper;
    private final RepairProposalService proposalService;
    private final BadCaseStateService badCaseStateService;
    private final AgentRunPersistenceService runPersistenceService;
    private final ObjectMapper objectMapper;

    public ProposalReviewPersistenceService(RepairProposalMapper proposalMapper,
                                            RepairProposalService proposalService,
                                            BadCaseStateService badCaseStateService,
                                            AgentRunPersistenceService runPersistenceService,
                                            ObjectMapper objectMapper) {
        this.proposalMapper = proposalMapper;
        this.proposalService = proposalService;
        this.badCaseStateService = badCaseStateService;
        this.runPersistenceService = runPersistenceService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RepairProposal saveDecision(Long userId,
                                       Long proposalId,
                                       ReviewerDecision decision) {
        RepairProposal proposal = proposalService.getOwned(userId, proposalId);
        if (!RepairProposalStatus.DRAFT.name().equals(proposal.getStatus())) {
            throw new BizException(ResultCode.CONFLICT,
                    "repair proposal is not awaiting review");
        }
        proposal.setReviewerVerdict(decision.verdict().name());
        proposal.setReviewerFindingsJson(serialize(decision));

        ReviewerVerdict verdict = decision.verdict();
        if (verdict == ReviewerVerdict.PASS) {
            proposal.setStatus(RepairProposalStatus.AWAITING_APPROVAL.name());
        } else if (verdict == ReviewerVerdict.REJECT
                || (verdict == ReviewerVerdict.REVISE && proposal.getRevisionNo() >= 1)) {
            proposal.setStatus(RepairProposalStatus.REJECTED.name());
        } else {
            proposal.setStatus(RepairProposalStatus.REVIEWED.name());
        }
        updateOrThrowConflict(proposal);

        badCaseStateService.transition(
                userId, proposal.getBadCaseId(), BadCaseStatus.PROPOSED, BadCaseStatus.REVIEWED);
        if (RepairProposalStatus.AWAITING_APPROVAL.name().equals(proposal.getStatus())) {
            badCaseStateService.transition(
                    userId, proposal.getBadCaseId(),
                    BadCaseStatus.REVIEWED, BadCaseStatus.AWAITING_APPROVAL);
        } else if (RepairProposalStatus.REJECTED.name().equals(proposal.getStatus())) {
            badCaseStateService.transition(
                    userId, proposal.getBadCaseId(),
                    BadCaseStatus.REVIEWED, BadCaseStatus.NO_ACTION);
        }
        return proposal;
    }

    @Transactional
    public RepairProposal saveDecisionAndCompleteRun(Long userId,
                                                     Long proposalId,
                                                     Long runId,
                                                     ReviewerDecision decision) {
        RepairProposal reviewed = saveDecision(userId, proposalId, decision);
        AgentRunStatus status = runPersistenceService.markSucceededInCurrentTransaction(
                userId,
                runId,
                "verdict=" + decision.verdict().name()
                        + ";findings=" + decision.findings().size()
                        + ";proposalStatus=" + reviewed.getStatus());
        if (status != AgentRunStatus.SUCCEEDED) {
            throw new BizException(ResultCode.CONFLICT,
                    "review run could not be completed: " + status);
        }
        return reviewed;
    }

    private void updateOrThrowConflict(RepairProposal proposal) {
        if (proposalMapper.updateById(proposal) != 1) {
            throw new BizException(ResultCode.CONFLICT,
                    "repair proposal changed concurrently; reload the latest state");
        }
    }

    private String serialize(ReviewerDecision decision) {
        try {
            return objectMapper.writeValueAsString(decision);
        } catch (JsonProcessingException ex) {
            throw new BizException(ResultCode.INTERNAL_ERROR,
                    "failed to serialize reviewer decision");
        }
    }
}
