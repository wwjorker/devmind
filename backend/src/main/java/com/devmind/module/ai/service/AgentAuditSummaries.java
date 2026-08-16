package com.devmind.module.ai.service;

import com.devmind.common.exception.BizException;
import com.devmind.module.ai.agent.AgentModelRequest;
import com.devmind.module.ai.agent.AgentModelResponse;

import java.util.stream.Collectors;

final class AgentAuditSummaries {

    static final int MAX_SUMMARY_LENGTH = 2_000;
    static final int MAX_ERROR_LENGTH = 500;

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

    static String errorMessage(Throwable error) {
        if (error == null) {
            return null;
        }
        if (error instanceof BizException && error.getMessage() != null) {
            return limit(normalize(error.getMessage()), MAX_ERROR_LENGTH);
        }
        return "external model call failed";
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
}
