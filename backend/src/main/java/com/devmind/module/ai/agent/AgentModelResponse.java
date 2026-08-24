package com.devmind.module.ai.agent;

import org.springframework.util.StringUtils;

import java.util.Objects;

public record AgentModelResponse(
        AgentMessage assistantMessage,
        String finishReason,
        String modelProvider,
        AgentTokenUsage usage
) {

    public AgentModelResponse {
        assistantMessage = Objects.requireNonNull(assistantMessage, "assistantMessage must not be null");
        usage = Objects.requireNonNull(usage, "usage must not be null");
        if (assistantMessage.role() != AgentMessage.Role.ASSISTANT) {
            throw new IllegalArgumentException("model response message must have assistant role");
        }
        if (!StringUtils.hasText(finishReason)) {
            throw new IllegalArgumentException("finishReason must not be blank");
        }
        if (!StringUtils.hasText(modelProvider)) {
            throw new IllegalArgumentException("modelProvider must not be blank");
        }
        boolean toolCallFinish = "tool_calls".equals(finishReason);
        if (toolCallFinish != !assistantMessage.toolCalls().isEmpty()) {
            throw new IllegalArgumentException("finishReason and assistant tool calls are inconsistent");
        }
    }
}
