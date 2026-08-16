package com.devmind.module.ai.service;

import com.devmind.common.api.ResultCode;
import com.devmind.common.exception.BizException;
import com.devmind.module.ai.agent.AgentBudgetRejection;
import com.devmind.module.ai.agent.AgentRunStatus;
import com.devmind.module.ai.agent.AgentStepReservation;

final class AgentStepExecutionErrors {

    private AgentStepExecutionErrors() {
    }

    static BizException rejectedRun(AgentStepReservation reservation) {
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

    static void requireActive(AgentRunStatus status) {
        if (status == AgentRunStatus.TIMED_OUT) {
            throw new BizException(ResultCode.SERVICE_UNAVAILABLE, "agent run timed out");
        }
        if (status != AgentRunStatus.RUNNING) {
            throw new BizException(ResultCode.CONFLICT,
                    "agent run is no longer active: " + status);
        }
    }
}
