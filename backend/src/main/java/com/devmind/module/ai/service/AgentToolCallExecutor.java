package com.devmind.module.ai.service;

import com.devmind.common.api.ResultCode;
import com.devmind.common.exception.BizException;
import com.devmind.module.ai.agent.AgentMessage;
import com.devmind.module.ai.agent.AgentReadTool;
import com.devmind.module.ai.agent.AgentRole;
import com.devmind.module.ai.agent.AgentRunStatus;
import com.devmind.module.ai.agent.AgentStepReservation;
import com.devmind.module.ai.agent.AgentToolCall;
import com.devmind.module.ai.agent.AgentToolContext;
import com.devmind.module.ai.tool.AgentReadToolRegistry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Service
public class AgentToolCallExecutor {

    private static final int MAX_RESULT_CHARS = 16_000;

    private final AgentRunPersistenceService persistenceService;
    private final AgentReadToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;

    public AgentToolCallExecutor(AgentRunPersistenceService persistenceService,
                                 AgentReadToolRegistry toolRegistry,
                                 ObjectMapper objectMapper) {
        this.persistenceService = persistenceService;
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AgentMessage execute(AgentToolContext context,
                                AgentRole role,
                                AgentToolCall toolCall) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(toolCall, "toolCall must not be null");

        JsonNode arguments = parseArgumentsOrNull(toolCall.arguments());
        AgentStepReservation reservation = persistenceService.reserveToolStep(
                context.userId(),
                context.runId(),
                role,
                toolCall.name(),
                toolCall.id(),
                AgentAuditSummaries.toolRequest(toolCall, arguments)
        );
        if (!reservation.permitted()) {
            throw AgentStepExecutionErrors.rejectedRun(reservation);
        }

        long startedNanos = System.nanoTime();
        JsonNode result;
        String content;
        try {
            if (arguments == null || !arguments.isObject()) {
                throw new BizException(ResultCode.BAD_REQUEST,
                        "tool arguments must be a JSON object");
            }
            AgentReadTool tool = toolRegistry.requireAllowed(toolCall.name());
            result = Objects.requireNonNull(
                    tool.execute(context, arguments), "tool result must not be null");
            content = serialize(result);
            if (content.length() > MAX_RESULT_CHARS) {
                throw new BizException(ResultCode.SERVICE_UNAVAILABLE,
                        "agent tool result exceeds the configured size limit");
            }
        } catch (RuntimeException ex) {
            persistenceService.failToolStep(
                    context.userId(),
                    context.runId(),
                    reservation.stepId(),
                    ex,
                    elapsedMillis(startedNanos)
            );
            throw ex;
        }
        AgentRunStatus status = persistenceService.completeToolStep(
                context.userId(),
                context.runId(),
                reservation.stepId(),
                elapsedMillis(startedNanos),
                AgentAuditSummaries.toolResponse(toolCall.name(), result, content.length())
        );
        AgentStepExecutionErrors.requireActive(status);
        return AgentMessage.toolResult(toolCall.id(), content);
    }

    private JsonNode parseArgumentsOrNull(String arguments) {
        try {
            return objectMapper.readTree(arguments);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private String serialize(JsonNode result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize agent tool result", ex);
        }
    }

    private long elapsedMillis(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }
}
