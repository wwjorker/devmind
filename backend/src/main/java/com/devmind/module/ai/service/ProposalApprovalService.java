package com.devmind.module.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devmind.common.api.ResultCode;
import com.devmind.common.exception.BizException;
import com.devmind.module.ai.agent.ApprovalDecision;
import com.devmind.module.ai.agent.BadCaseStatus;
import com.devmind.module.ai.agent.RepairProposalStatus;
import com.devmind.module.ai.agent.RepairProposalType;
import com.devmind.module.ai.entity.RepairProposal;
import com.devmind.module.ai.mapper.RepairProposalMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Objects;

@Service
public class ProposalApprovalService {

    private final RepairProposalMapper proposalMapper;
    private final RepairProposalService proposalService;
    private final ProposalValidator proposalValidator;
    private final BadCaseStateService badCaseStateService;
    private final ObjectMapper objectMapper;

    public ProposalApprovalService(RepairProposalMapper proposalMapper,
                                   RepairProposalService proposalService,
                                   ProposalValidator proposalValidator,
                                   BadCaseStateService badCaseStateService,
                                   ObjectMapper objectMapper) {
        this.proposalMapper = proposalMapper;
        this.proposalService = proposalService;
        this.proposalValidator = proposalValidator;
        this.badCaseStateService = badCaseStateService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RepairProposal decide(Long userId,
                                 Long proposalId,
                                 ProposalApprovalCommand command) {
        Objects.requireNonNull(command, "approval command must not be null");
        Objects.requireNonNull(command.decision(), "approval decision must not be null");
        String idempotencyKey = requireIdempotencyKey(command.idempotencyKey());
        String comment = normalizeComment(command.comment());
        if (command.decision() != ApprovalDecision.APPROVE_WITH_EDIT
                && StringUtils.hasText(command.editedDiffJson())) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "edited diff is only allowed with APPROVE_WITH_EDIT");
        }
        if (command.decision() == ApprovalDecision.REJECT && comment == null) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "rejection comment is required");
        }
        RepairProposal proposal = proposalService.getOwned(userId, proposalId);

        if (StringUtils.hasText(proposal.getApprovalIdempotencyKey())) {
            ensureEquivalentDecision(proposal, command, idempotencyKey, comment);
            return proposal;
        }
        if (!RepairProposalStatus.AWAITING_APPROVAL.name().equals(proposal.getStatus())
                || !"PASS".equals(proposal.getReviewerVerdict())) {
            throw new BizException(ResultCode.CONFLICT,
                    "repair proposal is not awaiting human approval");
        }

        String approvedDiff = null;
        if (command.decision() != ApprovalDecision.REJECT) {
            RepairProposalType type = RepairProposalType.valueOf(proposal.getProposalType());
            if (!type.isExecutable()) {
                throw new BizException(ResultCode.BAD_REQUEST,
                        "this proposal type is review-only and cannot be approved for execution");
            }
            String candidateDiff = command.decision() == ApprovalDecision.APPROVE_WITH_EDIT
                    ? requireEditedDiff(command.editedDiffJson())
                    : proposal.getDiffJson();
            RepairProposalDraft candidate = new RepairProposalDraft(
                    type,
                    proposal.getTargetDocumentId(),
                    proposal.getBaseVersionNo(),
                    candidateDiff,
                    proposal.getEvidenceJson(),
                    proposal.getCounterevidenceJson(),
                    proposal.getImpactJson(),
                    proposal.getRegressionPlanJson());
            approvedDiff = proposalValidator.validate(
                    userId, proposal.getBadCaseId(), candidate).diffJson();
        }

        proposal.setApprovalDecision(command.decision().name());
        proposal.setApprovalIdempotencyKey(idempotencyKey);
        proposal.setApprovedDiffJson(approvedDiff);
        proposal.setDecisionComment(comment);
        proposal.setStatus(command.decision() == ApprovalDecision.REJECT
                ? RepairProposalStatus.REJECTED.name()
                : RepairProposalStatus.APPROVED.name());
        try {
            updateOrThrowConflict(proposal);
            badCaseStateService.transition(
                    userId,
                    proposal.getBadCaseId(),
                    BadCaseStatus.AWAITING_APPROVAL,
                    command.decision() == ApprovalDecision.REJECT
                            ? BadCaseStatus.NO_ACTION
                            : BadCaseStatus.APPROVED);
            return proposal;
        } catch (DuplicateKeyException ex) {
            RepairProposal concurrent = findByApprovalKey(userId, idempotencyKey);
            if (concurrent == null || !Objects.equals(concurrent.getId(), proposalId)) {
                throw ex;
            }
            ensureEquivalentDecision(concurrent, command, idempotencyKey, comment);
            return concurrent;
        }
    }

    private RepairProposal findByApprovalKey(Long userId, String key) {
        return proposalMapper.selectOne(new LambdaQueryWrapper<RepairProposal>()
                .eq(RepairProposal::getUserId, userId)
                .eq(RepairProposal::getApprovalIdempotencyKey, key));
    }

    private void ensureEquivalentDecision(RepairProposal proposal,
                                          ProposalApprovalCommand command,
                                          String key,
                                          String comment) {
        String requestedDiff = command.decision() == ApprovalDecision.APPROVE_WITH_EDIT
                ? command.editedDiffJson()
                : command.decision() == ApprovalDecision.APPROVE
                        ? proposal.getDiffJson()
                        : null;
        if (!key.equals(proposal.getApprovalIdempotencyKey())
                || !command.decision().name().equals(proposal.getApprovalDecision())
                || !Objects.equals(comment, proposal.getDecisionComment())
                || !nullableJsonEquals(requestedDiff, proposal.getApprovedDiffJson())) {
            throw new BizException(ResultCode.CONFLICT,
                    "approval idempotency key was reused with different input");
        }
    }

    private boolean nullableJsonEquals(String left, String right) {
        if (left == null || right == null) return left == null && right == null;
        try {
            JsonNode leftNode = objectMapper.readTree(left);
            JsonNode rightNode = objectMapper.readTree(right);
            return Objects.equals(leftNode, rightNode);
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            return false;
        }
    }

    private void updateOrThrowConflict(RepairProposal proposal) {
        if (proposalMapper.updateById(proposal) != 1) {
            throw new BizException(ResultCode.CONFLICT,
                    "repair proposal changed concurrently; reload the latest state");
        }
    }

    private String requireIdempotencyKey(String value) {
        if (!StringUtils.hasText(value) || value.trim().length() > 128) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "approval idempotency key must be non-blank and at most 128 characters");
        }
        return value.trim();
    }

    private String normalizeComment(String value) {
        if (!StringUtils.hasText(value)) return null;
        String trimmed = value.trim();
        if (trimmed.length() > 500) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "approval comment must be at most 500 characters");
        }
        return trimmed;
    }

    private String requireEditedDiff(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "edited diff is required for APPROVE_WITH_EDIT");
        }
        return value;
    }
}
