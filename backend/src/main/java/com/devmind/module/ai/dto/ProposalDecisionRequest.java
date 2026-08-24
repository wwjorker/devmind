package com.devmind.module.ai.dto;

import com.devmind.module.ai.agent.ApprovalDecision;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProposalDecisionRequest(
        @NotNull ApprovalDecision decision,
        @NotBlank @Size(max = 128) String idempotencyKey,
        @Size(max = 4_000) String editedDiffJson,
        @Size(max = 500) String comment
) {
}
