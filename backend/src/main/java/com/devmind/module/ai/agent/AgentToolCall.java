package com.devmind.module.ai.agent;

import org.springframework.util.StringUtils;

public record AgentToolCall(String id, String type, String name, String arguments) {

    public static final String FUNCTION_TYPE = "function";

    public AgentToolCall {
        requireText(id, "tool call id");
        requireText(type, "tool call type");
        requireText(name, "tool call function name");
        requireText(arguments, "tool call arguments");
        if (!FUNCTION_TYPE.equals(type)) {
            throw new IllegalArgumentException("unsupported tool call type: " + type);
        }
    }

    public static AgentToolCall function(String id, String name, String arguments) {
        return new AgentToolCall(id, FUNCTION_TYPE, name, arguments);
    }

    private static void requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
