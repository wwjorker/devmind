package com.devmind.module.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devmind.common.api.ResultCode;
import com.devmind.common.exception.BizException;
import com.devmind.module.ai.entity.AiBadCase;
import com.devmind.module.ai.entity.RepairProposal;
import com.devmind.module.ai.mapper.AiBadCaseMapper;
import com.devmind.module.ai.mapper.RepairProposalMapper;
import com.devmind.module.ai.vo.RepairCaseDetailResponse;
import com.devmind.module.ai.vo.RepairCaseSummaryResponse;
import com.devmind.module.ai.vo.RepairProposalResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class RepairWorkflowQueryService {

    private static final int MAX_CASES = 100;

    private final AiBadCaseMapper badCaseMapper;
    private final RepairProposalMapper proposalMapper;

    public RepairWorkflowQueryService(AiBadCaseMapper badCaseMapper,
                                      RepairProposalMapper proposalMapper) {
        this.badCaseMapper = badCaseMapper;
        this.proposalMapper = proposalMapper;
    }

    public List<RepairCaseSummaryResponse> list(Long userId, String status) {
        LambdaQueryWrapper<AiBadCase> query = new LambdaQueryWrapper<AiBadCase>()
                .eq(AiBadCase::getUserId, userId)
                .orderByDesc(AiBadCase::getUpdatedAt)
                .orderByDesc(AiBadCase::getId)
                .last("LIMIT " + MAX_CASES);
        if (StringUtils.hasText(status)) {
            query.eq(AiBadCase::getStatus, status.trim().toUpperCase());
        }
        return badCaseMapper.selectList(query).stream()
                .map(this::summary)
                .toList();
    }

    public RepairCaseDetailResponse detail(Long userId, Long badCaseId) {
        AiBadCase badCase = badCaseMapper.selectOne(new LambdaQueryWrapper<AiBadCase>()
                .eq(AiBadCase::getId, badCaseId)
                .eq(AiBadCase::getUserId, userId));
        if (badCase == null) {
            throw new BizException(ResultCode.NOT_FOUND, "bad case not found");
        }
        List<RepairProposalResponse> proposals = proposalMapper.selectList(
                        new LambdaQueryWrapper<RepairProposal>()
                                .eq(RepairProposal::getBadCaseId, badCaseId)
                                .eq(RepairProposal::getUserId, userId)
                                .orderByDesc(RepairProposal::getRevisionNo)
                                .orderByDesc(RepairProposal::getId))
                .stream().map(this::proposal).toList();
        return new RepairCaseDetailResponse(
                badCase.getId(), badCase.getSourceType(), badCase.getSourceRef(),
                badCase.getAskLogId(), badCase.getAskSnapshotJson(),
                badCase.getChunkSnapshotJson(), badCase.getTrustedSourceJson(),
                badCase.getPromptSchemaVersion(), badCase.getRootCause(),
                badCase.getDiagnosisJson(), badCase.getStatus(), badCase.getCreatedAt(),
                badCase.getUpdatedAt(), proposals);
    }

    public RepairProposalResponse proposal(Long userId, Long proposalId) {
        RepairProposal proposal = proposalMapper.selectOne(
                new LambdaQueryWrapper<RepairProposal>()
                        .eq(RepairProposal::getId, proposalId)
                        .eq(RepairProposal::getUserId, userId));
        if (proposal == null) {
            throw new BizException(ResultCode.NOT_FOUND, "repair proposal not found");
        }
        return proposal(proposal);
    }

    private RepairCaseSummaryResponse summary(AiBadCase value) {
        return new RepairCaseSummaryResponse(
                value.getId(), value.getSourceType(), value.getSourceRef(), value.getAskLogId(),
                value.getRootCause(), value.getStatus(), value.getCreatedAt(), value.getUpdatedAt());
    }

    private RepairProposalResponse proposal(RepairProposal value) {
        return new RepairProposalResponse(
                value.getId(), value.getProposalType(), value.getTargetDocumentId(),
                value.getBaseVersionNo(), value.getDiffJson(), value.getEvidenceJson(),
                value.getCounterevidenceJson(), value.getImpactJson(),
                value.getRegressionPlanJson(), value.getReviewerVerdict(),
                value.getReviewerFindingsJson(), value.getRevisionNo(), value.getStatus(),
                value.getApprovalDecision(), value.getApprovedDiffJson(),
                value.getDecisionComment(), value.getExecutionResultJson(),
                value.getErrorCode(), value.getErrorMessage(), value.getCreatedAt(),
                value.getUpdatedAt());
    }
}
