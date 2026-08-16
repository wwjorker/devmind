package com.devmind.module.ai.agent;

import org.springframework.util.StringUtils;

public record TriageEvidence(
        String toolCallId,
        Long askLogId,
        Long chunkId,
        String observation
) {

    public TriageEvidence {
        if (!StringUtils.hasText(toolCallId) || toolCallId.length() > 128) {
            throw new IllegalArgumentException("triage evidence requires a valid toolCallId");
        }
        if (askLogId != null && askLogId <= 0) {
            throw new IllegalArgumentException("askLogId must be positive");
        }
        if (chunkId != null && chunkId <= 0) {
            throw new IllegalArgumentException("chunkId must be positive");
        }
        if (!StringUtils.hasText(observation) || observation.length() > 500) {
            throw new IllegalArgumentException("triage evidence requires a bounded observation");
        }
    }
}
