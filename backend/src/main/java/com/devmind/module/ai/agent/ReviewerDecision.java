package com.devmind.module.ai.agent;

import org.springframework.util.StringUtils;

import java.util.List;

public record ReviewerDecision(
        ReviewerVerdict verdict,
        String summary,
        List<ReviewerFinding> findings,
        double confidence
) {
    public ReviewerDecision {
        if (verdict == null) {
            throw new IllegalArgumentException("reviewer verdict is required");
        }
        if (!StringUtils.hasText(summary) || summary.length() > 1_000) {
            throw new IllegalArgumentException("reviewer summary must be non-blank and bounded");
        }
        findings = findings == null ? List.of() : List.copyOf(findings);
        if (findings.size() > 10) {
            throw new IllegalArgumentException("reviewer findings must contain at most 10 items");
        }
        if (verdict != ReviewerVerdict.PASS && findings.isEmpty()) {
            throw new IllegalArgumentException("REVISE and REJECT require at least one finding");
        }
        if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1) {
            throw new IllegalArgumentException("reviewer confidence must be between 0 and 1");
        }
    }
}
