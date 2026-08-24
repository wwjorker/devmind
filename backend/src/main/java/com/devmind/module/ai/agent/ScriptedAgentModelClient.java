package com.devmind.module.ai.agent;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public final class ScriptedAgentModelClient implements AgentModelClient {

    private final List<AgentModelResponse> script;
    private final AtomicInteger cursor = new AtomicInteger();

    public ScriptedAgentModelClient(List<AgentModelResponse> script) {
        this.script = List.copyOf(Objects.requireNonNull(script, "script must not be null"));
    }

    @Override
    public boolean supports(String provider) {
        return "scripted".equalsIgnoreCase(provider);
    }

    @Override
    public AgentModelResponse complete(AgentModelRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        int step = cursor.getAndIncrement();
        if (step >= script.size()) {
            throw new IllegalStateException("scripted agent model response exhausted at step " + step);
        }
        return script.get(step);
    }

    public int consumedResponses() {
        return Math.min(cursor.get(), script.size());
    }
}
