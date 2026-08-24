package com.devmind.module.ai.vo;

import java.time.LocalDateTime;

public record RepairProposalResponse(
        Long id,
        String proposalType,
        Long targetDocumentId,
        Integer baseVersionNo,
        String diffJson,
        String evidenceJson,
        String counterevidenceJson,
        String impactJson,
        String regressionPlanJson,
        String reviewerVerdict,
        String reviewerFindingsJson,
        Integer revisionNo,
        String status,
        String approvalDecision,
        String approvedDiffJson,
        String decisionComment,
        String executionResultJson,
        String errorCode,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
