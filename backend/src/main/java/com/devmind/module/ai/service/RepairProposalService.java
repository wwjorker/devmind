package com.devmind.module.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devmind.common.api.ResultCode;
import com.devmind.common.exception.BizException;
import com.devmind.module.ai.agent.BadCaseStatus;
import com.devmind.module.ai.agent.RepairProposalStatus;
import com.devmind.module.ai.entity.RepairProposal;
import com.devmind.module.ai.mapper.RepairProposalMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

@Service
public class RepairProposalService {

    private final RepairProposalMapper proposalMapper;
    private final ProposalValidator proposalValidator;
    private final BadCaseStateService badCaseStateService;
    private final ObjectMapper objectMapper;

    public RepairProposalService(RepairProposalMapper proposalMapper,
                                 ProposalValidator proposalValidator,
                                 BadCaseStateService badCaseStateService,
                                 ObjectMapper objectMapper) {
        this.proposalMapper = proposalMapper;
        this.proposalValidator = proposalValidator;
        this.badCaseStateService = badCaseStateService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RepairProposal create(Long userId,
                                 Long badCaseId,
                                 String idempotencyKey,
                                 RepairProposalDraft draft) {
        String safeKey = requireIdempotencyKey(idempotencyKey);
        RepairProposal existing = findByIdempotencyKey(userId, safeKey);
        if (existing != null) {
            ensureEquivalent(existing, badCaseId, draft);
            return existing;
        }

        ValidatedRepairProposal validated = proposalValidator.validate(userId, badCaseId, draft);
        RepairProposal proposal = new RepairProposal();
        proposal.setUserId(userId);
        proposal.setBadCaseId(badCaseId);
        proposal.setProposalType(draft.type().name());
        proposal.setTargetDocumentId(draft.targetDocumentId());
        proposal.setBaseVersionNo(draft.baseVersionNo());
        proposal.setDiffJson(validated.diffJson());
        proposal.setEvidenceJson(validated.evidenceJson());
        proposal.setCounterevidenceJson(validated.counterevidenceJson());
        proposal.setImpactJson(validated.impactJson());
        proposal.setRegressionPlanJson(validated.regressionPlanJson());
        proposal.setRevisionNo(0);
        proposal.setStatus(RepairProposalStatus.DRAFT.name());
        proposal.setIdempotencyKey(safeKey);
        proposal.setLockVersion(0);
        try {
            proposalMapper.insert(proposal);
            badCaseStateService.transition(
                    userId, badCaseId, BadCaseStatus.TRIAGED, BadCaseStatus.PROPOSED);
            return proposal;
        } catch (DuplicateKeyException ex) {
            RepairProposal concurrent = findByIdempotencyKey(userId, safeKey);
            if (concurrent == null) {
                throw ex;
            }
            ensureEquivalent(concurrent, badCaseId, draft);
            return concurrent;
        }
    }

    public RepairProposal getOwned(Long userId, Long proposalId) {
        RepairProposal proposal = proposalMapper.selectOne(
                new LambdaQueryWrapper<RepairProposal>()
                        .eq(RepairProposal::getId, proposalId)
                        .eq(RepairProposal::getUserId, userId));
        if (proposal == null) {
            throw new BizException(ResultCode.NOT_FOUND, "repair proposal not found");
        }
        return proposal;
    }

    public List<RepairProposal> listOwnedForBadCase(Long userId, Long badCaseId) {
        return proposalMapper.selectList(new LambdaQueryWrapper<RepairProposal>()
                .eq(RepairProposal::getUserId, userId)
                .eq(RepairProposal::getBadCaseId, badCaseId)
                .orderByAsc(RepairProposal::getRevisionNo)
                .orderByAsc(RepairProposal::getId));
    }

    private RepairProposal findByIdempotencyKey(Long userId, String idempotencyKey) {
        return proposalMapper.selectOne(new LambdaQueryWrapper<RepairProposal>()
                .eq(RepairProposal::getUserId, userId)
                .eq(RepairProposal::getIdempotencyKey, idempotencyKey));
    }

    private void ensureEquivalent(RepairProposal existing,
                                  Long badCaseId,
                                  RepairProposalDraft draft) {
        if (draft == null
                || draft.type() == null
                || !Objects.equals(existing.getBadCaseId(), badCaseId)
                || !existing.getProposalType().equals(draft.type().name())
                || !Objects.equals(existing.getTargetDocumentId(), draft.targetDocumentId())
                || !Objects.equals(existing.getBaseVersionNo(), draft.baseVersionNo())
                || !jsonEquals(existing.getDiffJson(), draft.diffJson())
                || !jsonEquals(existing.getEvidenceJson(), draft.evidenceJson())
                || !jsonEquals(existing.getCounterevidenceJson(),
                        defaultJson(draft.counterevidenceJson(), "[]"))
                || !jsonEquals(existing.getImpactJson(), draft.impactJson())
                || !jsonEquals(existing.getRegressionPlanJson(), draft.regressionPlanJson())) {
            throw new BizException(ResultCode.CONFLICT,
                    "proposal idempotency key was reused with different input");
        }
    }

    private boolean jsonEquals(String left, String right) {
        try {
            JsonNode leftNode = objectMapper.readTree(left);
            JsonNode rightNode = objectMapper.readTree(right);
            return Objects.equals(leftNode, rightNode);
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            return false;
        }
    }

    private String requireIdempotencyKey(String value) {
        if (!StringUtils.hasText(value) || value.trim().length() > 128) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "proposal idempotency key must be non-blank and at most 128 characters");
        }
        return value.trim();
    }

    private String defaultJson(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
