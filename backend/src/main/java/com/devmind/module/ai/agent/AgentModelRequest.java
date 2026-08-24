package com.devmind.module.ai.agent;

import java.util.List;
import java.util.Objects;

public record AgentModelRequest(
        List<AgentMessage> messages,
        List<AgentToolDefinition> tools,
        AgentToolChoice toolChoice
) {

    public AgentModelRequest {
        messages = List.copyOf(Objects.requireNonNull(messages, "messages must not be null"));
        tools = List.copyOf(Objects.requireNonNull(tools, "tools must not be null"));
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("messages must not be empty");
        }
        if (tools.isEmpty() && toolChoice != null && !toolChoice.isNone()) {
            throw new IllegalArgumentException("tool choice requires at least one tool");
        }
    }
}
