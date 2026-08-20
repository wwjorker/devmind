package com.devmind.module.ai.agent;

import org.springframework.util.StringUtils;

public record ReviewerFinding(
        String code,
        ReviewerSeverity severity,
        String description,
        String evidencePath,
        String toolCallId,
        Long chunkId
) {
    public ReviewerFinding {
        if (!StringUtils.hasText(code) || code.length() > 64) {
            throw new IllegalArgumentException("reviewer finding requires a bounded code");
        }
        if (severity == null) {
            throw new IllegalArgumentException("reviewer finding severity is required");
        }
        if (!StringUtils.hasText(description) || description.length() > 1_000) {
            throw new IllegalArgumentException("reviewer finding requires a bounded description");
        }
        if (!StringUtils.hasText(evidencePath) || evidencePath.length() > 256) {
            throw new IllegalArgumentException("reviewer finding evidencePath is required");
        }
        if (toolCallId != null && (!StringUtils.hasText(toolCallId) || toolCallId.length() > 128)) {
            throw new IllegalArgumentException("reviewer finding has an invalid toolCallId");
        }
        if (chunkId != null && (chunkId <= 0 || toolCallId == null)) {
            throw new IllegalArgumentException("reviewer chunk evidence requires a toolCallId");
        }
    }
}
