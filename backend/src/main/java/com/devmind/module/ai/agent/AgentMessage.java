package com.devmind.module.ai.agent;

import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

public record AgentMessage(
        Role role,
        String content,
        List<AgentToolCall> toolCalls,
        String toolCallId
) {

    public AgentMessage {
        role = Objects.requireNonNull(role, "role must not be null");
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);

        switch (role) {
            case SYSTEM, USER -> {
                requireContent(content, role);
                requireNoToolFields(toolCalls, toolCallId, role);
            }
            case ASSISTANT -> {
                if (content == null && toolCalls.isEmpty()) {
                    throw new IllegalArgumentException("assistant message requires content or tool calls");
                }
                if (toolCallId != null) {
                    throw new IllegalArgumentException("assistant message must not have toolCallId");
                }
            }
            case TOOL -> {
                Objects.requireNonNull(content, "tool message content must not be null");
                if (!StringUtils.hasText(toolCallId)) {
                    throw new IllegalArgumentException("tool message requires toolCallId");
                }
                if (!toolCalls.isEmpty()) {
                    throw new IllegalArgumentException("tool message must not have tool calls");
                }
            }
        }
    }

    public static AgentMessage system(String content) {
        return new AgentMessage(Role.SYSTEM, content, List.of(), null);
    }

    public static AgentMessage user(String content) {
        return new AgentMessage(Role.USER, content, List.of(), null);
    }

    public static AgentMessage assistant(String content) {
        return new AgentMessage(Role.ASSISTANT, content, List.of(), null);
    }

    public static AgentMessage assistantToolCalls(String content, List<AgentToolCall> toolCalls) {
        return new AgentMessage(Role.ASSISTANT, content, toolCalls, null);
    }

    public static AgentMessage toolResult(String toolCallId, String content) {
        return new AgentMessage(Role.TOOL, content, List.of(), toolCallId);
    }

    private static void requireContent(String content, Role role) {
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException(role.wireValue + " message requires content");
        }
    }

    private static void requireNoToolFields(List<AgentToolCall> toolCalls, String toolCallId, Role role) {
        if (!toolCalls.isEmpty() || toolCallId != null) {
            throw new IllegalArgumentException(role.wireValue + " message must not have tool fields");
        }
    }

    public enum Role {
        SYSTEM("system"),
        USER("user"),
        ASSISTANT("assistant"),
        TOOL("tool");

        private final String wireValue;

        Role(String wireValue) {
            this.wireValue = wireValue;
        }

        public String wireValue() {
            return wireValue;
        }
    }
}
