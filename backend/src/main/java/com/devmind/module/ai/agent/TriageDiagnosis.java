package com.devmind.module.ai.agent;

import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

public record TriageDiagnosis(
        TriageRootCause rootCause,
        String summary,
        List<TriageEvidence> evidence,
        TriageRoute recommendedRoute,
        double confidence
) {

    public TriageDiagnosis {
        rootCause = Objects.requireNonNull(rootCause, "rootCause must not be null");
        recommendedRoute = Objects.requireNonNull(
                recommendedRoute, "recommendedRoute must not be null");
        if (rootCause.requiredRoute() != recommendedRoute) {
            throw new IllegalArgumentException("triage route does not match root cause");
        }
        if (!StringUtils.hasText(summary) || summary.length() > 1_000) {
            throw new IllegalArgumentException("triage summary must be non-blank and bounded");
        }
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        if (evidence.isEmpty() || evidence.size() > 10) {
            throw new IllegalArgumentException("triage diagnosis requires 1 to 10 evidence items");
        }
        if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1) {
            throw new IllegalArgumentException("triage confidence must be between 0 and 1");
        }
    }
}
