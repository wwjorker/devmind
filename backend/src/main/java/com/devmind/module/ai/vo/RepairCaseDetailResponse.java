package com.devmind.module.ai.vo;

import java.time.LocalDateTime;
import java.util.List;

public record RepairCaseDetailResponse(
        Long id,
        String sourceType,
        String sourceRef,
        Long askLogId,
        String askSnapshotJson,
        String chunkSnapshotJson,
        String trustedSourceJson,
        Integer promptSchemaVersion,
        String rootCause,
        String diagnosisJson,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<RepairProposalResponse> proposals
) {
}
