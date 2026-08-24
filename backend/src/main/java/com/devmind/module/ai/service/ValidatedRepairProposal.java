package com.devmind.module.ai.service;

public record ValidatedRepairProposal(
        String diffJson,
        String evidenceJson,
        String counterevidenceJson,
        String impactJson,
        String regressionPlanJson
) {
}
