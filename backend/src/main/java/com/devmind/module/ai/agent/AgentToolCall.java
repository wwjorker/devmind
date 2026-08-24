package com.devmind.module.ai.agent;

import org.springframework.util.StringUtils;

public record AgentToolCall(String id, String type, String name, String arguments) {

    public static final String FUNCTION_TYPE = "function";
    private static final int MAX_ID_LENGTH = 128;
    private static final int MAX_NAME_LENGTH = 64;
    private static final int MAX_ARGUMENTS_LENGTH = 8_000;

    public AgentToolCall {
        requireText(id, "tool call id");
        requireText(type, "tool call type");
        requireText(name, "tool call function name");
        requireText(arguments, "tool call arguments");
        if (!FUNCTION_TYPE.equals(type)) {
            throw new IllegalArgumentException("unsupported tool call type: " + type);
        }
        requireLength(id, MAX_ID_LENGTH, "tool call id");
        requireLength(name, MAX_NAME_LENGTH, "tool call function name");
        requireLength(arguments, MAX_ARGUMENTS_LENGTH, "tool call arguments");
    }

    public static AgentToolCall function(String id, String name, String arguments) {
        return new AgentToolCall(id, FUNCTION_TYPE, name, arguments);
    }

    private static void requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private static void requireLength(String value, int maxLength, String field) {
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(field + " is too long");
        }
    }
}
