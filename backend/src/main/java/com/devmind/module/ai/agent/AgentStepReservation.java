package com.devmind.module.ai.agent;

public record AgentStepReservation(
        Long stepId,
        Integer sequenceNo,
        AgentRunStatus runStatus,
        AgentBudgetRejection rejection
) {

    public boolean permitted() {
        return rejection == AgentBudgetRejection.NONE;
    }

    public static AgentStepReservation allowed(Long stepId, int sequenceNo) {
        return new AgentStepReservation(
                stepId,
                sequenceNo,
                AgentRunStatus.RUNNING,
                AgentBudgetRejection.NONE
        );
    }

    public static AgentStepReservation rejected(AgentRunStatus status, AgentBudgetRejection rejection) {
        return new AgentStepReservation(null, null, status, rejection);
    }
}
