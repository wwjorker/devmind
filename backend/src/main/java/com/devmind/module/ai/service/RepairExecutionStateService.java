package com.devmind.module.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devmind.common.api.ResultCode;
import com.devmind.common.exception.BizException;
import com.devmind.module.ai.agent.BadCaseStatus;
import com.devmind.module.ai.agent.RepairProposalStatus;
import com.devmind.module.ai.agent.RepairProposalType;
import com.devmind.module.ai.entity.RepairProposal;
import com.devmind.module.ai.mapper.RepairProposalMapper;
import com.devmind.module.document.entity.KnowledgeDocument;
import com.devmind.module.document.mapper.KnowledgeDocumentMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.Set;

@Service
public class RepairExecutionStateService {

    private final RepairProposalMapper proposalMapper;
    private final RepairProposalService proposalService;
    private final KnowledgeDocumentMapper documentMapper;
    private final BadCaseStateService badCaseStateService;
    private final ObjectMapper objectMapper;

    public RepairExecutionStateService(RepairProposalMapper proposalMapper,
                                       RepairProposalService proposalService,
                                       KnowledgeDocumentMapper documentMapper,
                                       BadCaseStateService badCaseStateService,
                                       ObjectMapper objectMapper) {
        this.proposalMapper = proposalMapper;
        this.proposalService = proposalService;
        this.documentMapper = documentMapper;
        this.badCaseStateService = badCaseStateService;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RepairProposal reserve(Long userId, Long proposalId, String executionKey) {
        String safeKey = requireKey(executionKey);
        RepairProposal proposal = proposalService.getOwned(userId, proposalId);
        if (StringUtils.hasText(proposal.getExecutionIdempotencyKey())) {
            if (!safeKey.equals(proposal.getExecutionIdempotencyKey())) {
                throw new BizException(ResultCode.CONFLICT,
                        "proposal already has a different execution key");
            }
            return proposal;
        }
        if (!RepairProposalStatus.APPROVED.name().equals(proposal.getStatus())) {
            throw new BizException(ResultCode.CONFLICT,
                    "repair proposal is not approved for execution");
        }
        RepairProposalType type = RepairProposalType.valueOf(proposal.getProposalType());
        if (!type.isExecutable() || !StringUtils.hasText(proposal.getApprovedDiffJson())) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "repair proposal type is not executable");
        }

        KnowledgeDocument document = documentMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeDocument>()
                        .eq(KnowledgeDocument::getId, proposal.getTargetDocumentId())
                        .eq(KnowledgeDocument::getUserId, userId));
        if (document == null || !Objects.equals(document.getVersionNo(), proposal.getBaseVersionNo())) {
            proposal.setExecutionIdempotencyKey(safeKey);
            proposal.setStatus(RepairProposalStatus.INVALIDATED.name());
            proposal.setErrorCode("STALE_BASE_VERSION");
            proposal.setErrorMessage("proposal base version is stale at execution time");
            proposal.setExecutionResultJson(resultJson(
                    "INVALIDATED", null, null, null,
                    proposal.getErrorMessage(), false));
            updateOrThrowConflict(proposal);
            badCaseStateService.transition(
                    userId, proposal.getBadCaseId(), BadCaseStatus.APPROVED, BadCaseStatus.FAILED);
            return proposal;
        }

        proposal.setExecutionIdempotencyKey(safeKey);
        proposal.setStatus(RepairProposalStatus.EXECUTING.name());
        proposal.setExecutionResultJson(resultJson(
                "RESERVED", null, null, null, null, false));
        try {
            updateOrThrowConflict(proposal);
            badCaseStateService.transition(
                    userId, proposal.getBadCaseId(), BadCaseStatus.APPROVED, BadCaseStatus.EXECUTING);
            return proposal;
        } catch (DuplicateKeyException ex) {
            RepairProposal concurrent = findByExecutionKey(userId, safeKey);
            if (concurrent == null || !Objects.equals(concurrent.getId(), proposalId)) throw ex;
            return concurrent;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RepairProposal markVerifying(Long userId,
                                        Long proposalId,
                                        String executionKey,
                                        int appliedVersionNo) {
        RepairProposal proposal = requireExecution(userId, proposalId, executionKey);
        if (RepairProposalStatus.VERIFYING.name().equals(proposal.getStatus())) return proposal;
        requireStatus(proposal, RepairProposalStatus.EXECUTING);
        proposal.setStatus(RepairProposalStatus.VERIFYING.name());
        proposal.setExecutionResultJson(resultJson(
                "DOCUMENT_APPLIED", appliedVersionNo, null, null, null, false));
        updateOrThrowConflict(proposal);
        badCaseStateService.transition(
                userId, proposal.getBadCaseId(), BadCaseStatus.EXECUTING, BadCaseStatus.VERIFYING);
        return proposal;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RepairProposal markApplied(Long userId,
                                      Long proposalId,
                                      String executionKey,
                                      int appliedVersionNo,
                                      RegressionResult regression) {
        RepairProposal proposal = requireExecution(userId, proposalId, executionKey);
        if (RepairProposalStatus.APPLIED.name().equals(proposal.getStatus())) return proposal;
        requireStatus(proposal, RepairProposalStatus.VERIFYING);
        proposal.setStatus(RepairProposalStatus.APPLIED.name());
        proposal.setExecutionResultJson(resultJson(
                "RESOLVED", appliedVersionNo, null, regression, null, false));
        proposal.setErrorCode(null);
        proposal.setErrorMessage(null);
        updateOrThrowConflict(proposal);
        badCaseStateService.transition(
                userId, proposal.getBadCaseId(), BadCaseStatus.VERIFYING, BadCaseStatus.RESOLVED);
        return proposal;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RepairProposal markRolledBack(Long userId,
                                         Long proposalId,
                                         String executionKey,
                                         int appliedVersionNo,
                                         int rollbackVersionNo,
                                         RegressionResult regression,
                                         String failureMessage,
                                         boolean indexRecoveryRequired) {
        RepairProposal proposal = requireExecution(userId, proposalId, executionKey);
        if (RepairProposalStatus.ROLLED_BACK.name().equals(proposal.getStatus())) return proposal;
        RepairProposalStatus current = RepairProposalStatus.valueOf(proposal.getStatus());
        if (!Set.of(RepairProposalStatus.EXECUTING, RepairProposalStatus.VERIFYING)
                .contains(current)) {
            throw new BizException(ResultCode.CONFLICT,
                    "proposal is not eligible for rollback completion");
        }
        proposal.setStatus(RepairProposalStatus.ROLLED_BACK.name());
        proposal.setErrorCode("EXECUTION_COMPENSATED");
        proposal.setErrorMessage(limit(failureMessage, 500));
        proposal.setExecutionResultJson(resultJson(
                "ROLLED_BACK", appliedVersionNo, rollbackVersionNo,
                regression, failureMessage, indexRecoveryRequired));
        updateOrThrowConflict(proposal);
        badCaseStateService.transition(
                userId,
                proposal.getBadCaseId(),
                current == RepairProposalStatus.EXECUTING
                        ? BadCaseStatus.EXECUTING : BadCaseStatus.VERIFYING,
                BadCaseStatus.ROLLED_BACK);
        return proposal;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RepairProposal markFailed(Long userId,
                                     Long proposalId,
                                     String executionKey,
                                     String errorCode,
                                     String failureMessage) {
        RepairProposal proposal = requireExecution(userId, proposalId, executionKey);
        if (Set.of(
                RepairProposalStatus.FAILED.name(),
                RepairProposalStatus.INVALIDATED.name()).contains(proposal.getStatus())) {
            return proposal;
        }
        RepairProposalStatus current = RepairProposalStatus.valueOf(proposal.getStatus());
        if (!Set.of(RepairProposalStatus.EXECUTING, RepairProposalStatus.VERIFYING)
                .contains(current)) {
            throw new BizException(ResultCode.CONFLICT,
                    "proposal is not eligible for failure completion");
        }
        proposal.setStatus(RepairProposalStatus.FAILED.name());
        proposal.setErrorCode(limit(errorCode, 64));
        proposal.setErrorMessage(limit(failureMessage, 500));
        proposal.setExecutionResultJson(resultJson(
                "FAILED", appliedVersion(proposal), null, null, failureMessage, true));
        updateOrThrowConflict(proposal);
        badCaseStateService.transition(
                userId,
                proposal.getBadCaseId(),
                current == RepairProposalStatus.EXECUTING
                        ? BadCaseStatus.EXECUTING : BadCaseStatus.VERIFYING,
                BadCaseStatus.FAILED);
        return proposal;
    }

    public Integer appliedVersion(RepairProposal proposal) {
        if (!StringUtils.hasText(proposal.getExecutionResultJson())) return null;
        try {
            JsonNode node = objectMapper.readTree(proposal.getExecutionResultJson());
            JsonNode version = node.get("appliedVersionNo");
            return version == null || !version.isInt() ? null : version.intValue();
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private RepairProposal requireExecution(Long userId, Long proposalId, String executionKey) {
        RepairProposal proposal = proposalService.getOwned(userId, proposalId);
        if (!Objects.equals(proposal.getExecutionIdempotencyKey(), executionKey)) {
            throw new BizException(ResultCode.CONFLICT, "execution key does not match proposal");
        }
        return proposal;
    }

    private void requireStatus(RepairProposal proposal, RepairProposalStatus expected) {
        if (!expected.name().equals(proposal.getStatus())) {
            throw new BizException(ResultCode.CONFLICT,
                    "proposal status changed; expected " + expected);
        }
    }

    private RepairProposal findByExecutionKey(Long userId, String key) {
        return proposalMapper.selectOne(new LambdaQueryWrapper<RepairProposal>()
                .eq(RepairProposal::getUserId, userId)
                .eq(RepairProposal::getExecutionIdempotencyKey, key));
    }

    private String resultJson(String phase,
                              Integer appliedVersionNo,
                              Integer rollbackVersionNo,
                              RegressionResult regression,
                              String message,
                              boolean indexRecoveryRequired) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("phase", phase);
        if (appliedVersionNo == null) node.putNull("appliedVersionNo");
        else node.put("appliedVersionNo", appliedVersionNo);
        if (rollbackVersionNo == null) node.putNull("rollbackVersionNo");
        else node.put("rollbackVersionNo", rollbackVersionNo);
        if (regression == null) node.putNull("regression");
        else node.set("regression", objectMapper.valueToTree(regression));
        if (message == null) node.putNull("message"); else node.put("message", limit(message, 1_000));
        node.put("indexRecoveryRequired", indexRecoveryRequired);
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException ex) {
            throw new BizException(ResultCode.INTERNAL_ERROR,
                    "failed to serialize repair execution result");
        }
    }

    private void updateOrThrowConflict(RepairProposal proposal) {
        if (proposalMapper.updateById(proposal) != 1) {
            throw new BizException(ResultCode.CONFLICT,
                    "repair proposal changed concurrently; reload the latest state");
        }
    }

    private String requireKey(String key) {
        if (!StringUtils.hasText(key) || key.trim().length() > 128) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "execution idempotency key must be non-blank and at most 128 characters");
        }
        return key.trim();
    }

    private String limit(String value, int maxChars) {
        if (value == null) return null;
        return value.length() <= maxChars ? value : value.substring(0, maxChars);
    }
}
