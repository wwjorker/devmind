package com.devmind.module.ai.service;

import com.devmind.common.exception.BizException;
import com.devmind.module.ai.agent.AgentModelRequest;
import com.devmind.module.ai.agent.AgentModelResponse;
import com.devmind.module.ai.agent.AgentToolCall;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Set;
import java.util.stream.Collectors;

final class AgentAuditSummaries {

    static final int MAX_SUMMARY_LENGTH = 2_000;
    static final int MAX_ERROR_LENGTH = 500;
    private static final Set<String> SAFE_TOOL_ARGUMENT_FIELDS = Set.of(
            "query", "limit", "chunkIds", "askLogId");

    private AgentAuditSummaries() {
    }

    static String modelRequest(AgentModelRequest request) {
        String roles = request.messages().stream()
                .map(message -> message.role().wireValue())
                .collect(Collectors.joining(","));
        String tools = request.tools().stream()
                .map(tool -> tool.name())
                .collect(Collectors.joining(","));
        String toolChoice = request.toolChoice() == null
                ? "default"
                : request.toolChoice().mode().name();
        return limit("messages=" + request.messages().size()
                + ";roles=" + roles
                + ";tools=" + tools
                + ";toolChoice=" + toolChoice, MAX_SUMMARY_LENGTH);
    }

    static String modelResponse(AgentModelResponse response) {
        String tools = response.assistantMessage().toolCalls().stream()
                .map(call -> call.name())
                .collect(Collectors.joining(","));
        int contentChars = response.assistantMessage().content() == null
                ? 0
                : response.assistantMessage().content().length();
        return limit("finishReason=" + response.finishReason()
                + ";contentChars=" + contentChars
                + ";toolCalls=" + tools, MAX_SUMMARY_LENGTH);
    }

    static String result(String summary) {
        return limit(normalize(summary), MAX_SUMMARY_LENGTH);
    }

    static String toolRequest(AgentToolCall call, JsonNode arguments) {
        String fields = arguments != null && arguments.isObject()
                ? safeArgumentFields(arguments)
                : "invalid";
        return limit("tool=" + call.name()
                + ";argumentFields=" + fields
                + ";argumentChars=" + call.arguments().length(), MAX_SUMMARY_LENGTH);
    }

    static String toolResponse(String toolName, JsonNode result, int serializedChars) {
        int items = result != null && result.has("items") && result.get("items").isArray()
                ? result.get("items").size()
                : 0;
        return limit("tool=" + toolName
                + ";resultChars=" + serializedChars
                + ";items=" + items, MAX_SUMMARY_LENGTH);
    }

    static String modelErrorMessage(Throwable error) {
        return safeErrorMessage(error, "external model call failed");
    }

    static String toolErrorMessage(Throwable error) {
        return safeErrorMessage(error, "agent tool execution failed");
    }

    private static String safeErrorMessage(Throwable error, String fallback) {
        if (error == null) {
            return null;
        }
        if (error instanceof BizException && error.getMessage() != null) {
            return limit(normalize(error.getMessage()), MAX_ERROR_LENGTH);
        }
        return fallback;
    }

    static String errorCode(Throwable error) {
        if (error == null) {
            return null;
        }
        return limit(error.getClass().getSimpleName(), 64);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        return value.replace('\r', ' ').replace('\n', ' ').trim();
    }

    private static String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private static String safeArgumentFields(JsonNode arguments) {
        java.util.List<String> values = new java.util.ArrayList<>();
        arguments.fieldNames().forEachRemaining(field -> values.add(
                SAFE_TOOL_ARGUMENT_FIELDS.contains(field) ? field : "<unsupported>"));
        return String.join(",", values);
    }
}
