package com.devmind.module.ai.vo;

import java.time.LocalDateTime;

public record RepairCaseSummaryResponse(
        Long id,
        String sourceType,
        String sourceRef,
        Long askLogId,
        String rootCause,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
