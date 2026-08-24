package com.devmind.module.ai.agent;

public interface AgentModelClient {

    boolean supports(String provider);

    AgentModelResponse complete(AgentModelRequest request);
}
