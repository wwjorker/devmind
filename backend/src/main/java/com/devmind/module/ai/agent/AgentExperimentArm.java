package com.devmind.module.ai.agent;

public enum AgentExperimentArm {
    RULES("rules"),
    SINGLE("single"),
    SINGLE_SELF_REVIEW("single+self-review"),
    REVIEWED_MULTI("reviewed-multi");

    private final String wireValue;

    AgentExperimentArm(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
