package com.devmind.module.ai.agent;

import org.springframework.util.StringUtils;

public record EvidenceTriageInput(
        Long askLogId,
        String issueDescription,
        String expectedAnswer
) {

    public EvidenceTriageInput {
        if (askLogId == null || askLogId <= 0) {
            throw new IllegalArgumentException("askLogId must be positive");
        }
        if (!StringUtils.hasText(issueDescription) || issueDescription.length() > 1_000) {
            throw new IllegalArgumentException(
                    "issueDescription must be non-blank and at most 1000 characters");
        }
        issueDescription = issueDescription.trim();
        if (expectedAnswer != null && expectedAnswer.length() > 4_000) {
            throw new IllegalArgumentException("expectedAnswer must be at most 4000 characters");
        }
        expectedAnswer = StringUtils.hasText(expectedAnswer) ? expectedAnswer.trim() : null;
    }
}
