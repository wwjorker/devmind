package com.devmind.module.ai.service;

import com.devmind.common.api.ResultCode;
import com.devmind.common.exception.BizException;
import com.devmind.module.ai.agent.AgentBudgetRejection;
import com.devmind.module.ai.agent.AgentModelClient;
import com.devmind.module.ai.agent.AgentModelRequest;
import com.devmind.module.ai.agent.AgentModelResponse;
import com.devmind.module.ai.agent.AgentRole;
import com.devmind.module.ai.agent.AgentRunStatus;
import com.devmind.module.ai.agent.AgentStepReservation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Service
public class AgentModelStepExecutor {

    private final AgentRunPersistenceService persistenceService;

    public AgentModelStepExecutor(AgentRunPersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AgentModelResponse execute(Long userId,
                                      Long runId,
                                      AgentRole role,
                                      AgentModelClient modelClient,
                                      AgentModelRequest request) {
        Objects.requireNonNull(modelClient, "modelClient must not be null");
        Objects.requireNonNull(request, "request must not be null");
        AgentStepReservation reservation = persistenceService.reserveModelStep(
                userId,
                runId,
                role,
                AgentAuditSummaries.modelRequest(request)
        );
        if (!reservation.permitted()) {
            throw rejectedRun(reservation);
        }

        long startedNanos = System.nanoTime();
        AgentModelResponse response;
        try {
            response = modelClient.complete(request);
        } catch (RuntimeException ex) {
            persistenceService.failModelStep(
                    userId,
                    runId,
                    reservation.stepId(),
                    ex,
                    elapsedMillis(startedNanos)
            );
            throw ex;
        }

        AgentRunStatus status = persistenceService.completeModelStep(
                userId,
                runId,
                reservation.stepId(),
                response,
                elapsedMillis(startedNanos),
                AgentAuditSummaries.modelResponse(response)
        );
        if (status == AgentRunStatus.TIMED_OUT) {
            throw new BizException(ResultCode.SERVICE_UNAVAILABLE, "agent run timed out");
        }
        if (status != AgentRunStatus.RUNNING) {
            throw new BizException(ResultCode.CONFLICT, "agent run is no longer active: " + status);
        }
        return response;
    }

    private BizException rejectedRun(AgentStepReservation reservation) {
        if (reservation.rejection() == AgentBudgetRejection.DEADLINE_EXCEEDED
                || reservation.runStatus() == AgentRunStatus.TIMED_OUT) {
            return new BizException(ResultCode.SERVICE_UNAVAILABLE, "agent run timed out");
        }
        if (reservation.runStatus() == AgentRunStatus.BUDGET_EXHAUSTED
                || reservation.rejection() == AgentBudgetRejection.MAX_STEPS
                || reservation.rejection() == AgentBudgetRejection.MAX_MODEL_CALLS
                || reservation.rejection() == AgentBudgetRejection.MAX_TOTAL_TOKENS) {
            return new BizException(
                    ResultCode.TOO_MANY_REQUESTS,
                    "agent budget exhausted: " + reservation.rejection().name().toLowerCase()
            );
        }
        return new BizException(
                ResultCode.CONFLICT,
                "agent run is not active: " + reservation.runStatus()
        );
    }

    private long elapsedMillis(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }
}
