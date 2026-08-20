package com.devmind.module.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WorkflowKeyRequest(
        @NotBlank @Size(max = 100) String idempotencyKey
) {
}
