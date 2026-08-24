package com.devmind.module.ai.agent;

import org.springframework.util.StringUtils;

public record ChangeReviewInput(
        Long proposalId,
        Long badCaseId,
        String rootCause,
        String diagnosisJson,
        String proposalType,
        Long targetDocumentId,
        Integer baseVersionNo,
        String diffJson,
        String evidenceJson,
        String counterevidenceJson,
        String impactJson,
        String regressionPlanJson,
        String currentVersionJson,
        int revisionNo
) {
    public ChangeReviewInput {
        requirePositive(proposalId, "proposalId");
        requirePositive(badCaseId, "badCaseId");
        requireText(rootCause, "rootCause", 64);
        requireJson(diagnosisJson, "diagnosisJson", 24_000);
        requireText(proposalType, "proposalType", 32);
        requireJson(diffJson, "diffJson", 24_000);
        requireJson(evidenceJson, "evidenceJson", 24_000);
        requireJson(counterevidenceJson, "counterevidenceJson", 24_000);
        requireJson(impactJson, "impactJson", 8_000);
        requireJson(regressionPlanJson, "regressionPlanJson", 8_000);
        requireJson(currentVersionJson, "currentVersionJson", 24_000);
        if (revisionNo < 0 || revisionNo > 1) {
            throw new IllegalArgumentException("revisionNo must be 0 or 1");
        }
    }

    private static void requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }

    private static void requireText(String value, String field, int maxChars) {
        if (!StringUtils.hasText(value) || value.length() > maxChars) {
            throw new IllegalArgumentException(field + " must be non-blank and bounded");
        }
    }

    private static void requireJson(String value, String field, int maxChars) {
        requireText(value, field, maxChars);
    }
}
