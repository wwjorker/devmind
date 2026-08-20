package com.devmind.module.ai.service;

import com.devmind.common.api.ResultCode;
import com.devmind.common.exception.BizException;
import com.devmind.module.ai.agent.AgentBudgetLimits;
import com.devmind.module.ai.agent.AgentExperimentArm;
import com.devmind.module.ai.agent.AgentModelClient;
import com.devmind.module.ai.agent.AgentRunStatus;
import com.devmind.module.ai.agent.AgentToolContext;
import com.devmind.module.ai.agent.RepairProposalStatus;
import com.devmind.module.ai.entity.AgentRun;
import com.devmind.module.ai.entity.RepairProposal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class RepairWorkflowActionService {

    private final AgentOrchestrator agentOrchestrator;
    private final AgentRunPersistenceService runPersistenceService;
    private final RepairProposalService proposalService;
    private final ProposalReviewService reviewService;
    private final ProposalApprovalService approvalService;
    private final RepairExecutor repairExecutor;

    public RepairWorkflowActionService(AgentOrchestrator agentOrchestrator,
                                       AgentRunPersistenceService runPersistenceService,
                                       RepairProposalService proposalService,
                                       ProposalReviewService reviewService,
                                       ProposalApprovalService approvalService,
                                       RepairExecutor repairExecutor) {
        this.agentOrchestrator = agentOrchestrator;
        this.runPersistenceService = runPersistenceService;
        this.proposalService = proposalService;
        this.reviewService = reviewService;
        this.approvalService = approvalService;
        this.repairExecutor = repairExecutor;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public TriageWorkflowResult triage(Long userId,
                                       Long badCaseId,
                                       String idempotencyKey,
                                       AgentModelClient modelClient) {
        return agentOrchestrator.triageAndRoute(
                userId, badCaseId, requireKey(idempotencyKey), modelClient);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RepairProposal review(Long userId,
                                 Long proposalId,
                                 String idempotencyKey,
                                 AgentModelClient modelClient) {
        String key = requireKey(idempotencyKey) + ":review";
        RepairProposal proposal = proposalService.getOwned(userId, proposalId);
        if (!RepairProposalStatus.DRAFT.name().equals(proposal.getStatus())) {
            AgentRun existing = runPersistenceService.findOwnedByIdempotencyKey(userId, key);
            if (existing != null
                    && java.util.Objects.equals(existing.getBadCaseId(), proposal.getBadCaseId())
                    && AgentRunStatus.SUCCEEDED.name().equals(existing.getStatus())) {
                return proposal;
            }
            throw new BizException(ResultCode.CONFLICT,
                    "repair proposal is not awaiting review");
        }
        AgentRun run = runPersistenceService.startRun(
                userId,
                proposal.getBadCaseId(),
                AgentExperimentArm.REVIEWED_MULTI,
                AgentBudgetLimits.triageDefaults(),
                key);
        AgentRunStatus status = AgentRunStatus.valueOf(run.getStatus());
        if (status == AgentRunStatus.SUCCEEDED) {
            return proposalService.getOwned(userId, proposalId);
        }
        if (status != AgentRunStatus.RUNNING) {
            throw new BizException(ResultCode.CONFLICT,
                    "review workflow is already terminal: " + status);
        }
        return reviewService.review(
                userId,
                proposalId,
                new AgentToolContext(userId, run.getId()),
                modelClient);
    }

    public RepairProposal decide(Long userId,
                                 Long proposalId,
                                 ProposalApprovalCommand command) {
        return approvalService.decide(userId, proposalId, command);
    }

    public RepairProposal execute(Long userId,
                                  Long proposalId,
                                  String idempotencyKey) {
        return repairExecutor.execute(
                userId, proposalId, requireKey(idempotencyKey) + ":execute");
    }

    private String requireKey(String key) {
        if (!StringUtils.hasText(key) || key.trim().length() > 100) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "workflow idempotency key must be non-blank and at most 100 characters");
        }
        return key.trim();
    }
}
