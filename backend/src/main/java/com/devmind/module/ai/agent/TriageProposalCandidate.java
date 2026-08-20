package com.devmind.module.ai.agent;

import org.springframework.util.StringUtils;

import java.util.Objects;

public record TriageProposalCandidate(
        RepairProposalType type,
        Long targetDocumentId,
        Integer baseVersionNo,
        String diffJson,
        String evidenceJson,
        String counterevidenceJson,
        String impactJson,
        String regressionPlanJson
) {
    public TriageProposalCandidate {
        type = Objects.requireNonNull(type, "proposal type must not be null");
        if (type != RepairProposalType.METADATA_PATCH) {
            throw new IllegalArgumentException(
                    "triage may submit only a metadata patch candidate");
        }
        if (targetDocumentId == null || targetDocumentId <= 0
                || baseVersionNo == null || baseVersionNo <= 0) {
            throw new IllegalArgumentException(
                    "metadata proposal requires a target document and base version");
        }
        requireJson(diffJson, "diff");
        requireJson(evidenceJson, "evidence");
        requireJson(counterevidenceJson, "counterevidence");
        requireJson(impactJson, "impact");
        requireJson(regressionPlanJson, "regressionPlan");
    }

    private static void requireJson(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
