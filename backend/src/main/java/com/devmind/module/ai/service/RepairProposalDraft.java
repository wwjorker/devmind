package com.devmind.module.ai.service;

import com.devmind.module.ai.agent.RepairProposalType;

public record RepairProposalDraft(
        RepairProposalType type,
        Long targetDocumentId,
        Integer baseVersionNo,
        String diffJson,
        String evidenceJson,
        String counterevidenceJson,
        String impactJson,
        String regressionPlanJson
) {
}
