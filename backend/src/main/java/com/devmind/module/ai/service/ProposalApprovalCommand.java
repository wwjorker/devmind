package com.devmind.module.ai.service;

import com.devmind.module.ai.agent.ApprovalDecision;

public record ProposalApprovalCommand(
        ApprovalDecision decision,
        String idempotencyKey,
        String editedDiffJson,
        String comment
) {
}
