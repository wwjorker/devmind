package com.devmind.module.ai.agent;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.util.StringUtils;

import java.util.Objects;

public record AgentToolDefinition(String name, String description, JsonNode parameters) {

    public AgentToolDefinition {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("tool name must not be blank");
        }
        if (!StringUtils.hasText(description)) {
            throw new IllegalArgumentException("tool description must not be blank");
        }
        parameters = Objects.requireNonNull(parameters, "tool parameters must not be null").deepCopy();
        if (!parameters.isObject()) {
            throw new IllegalArgumentException("tool parameters must be a JSON Schema object");
        }
    }

    @Override
    public JsonNode parameters() {
        return parameters.deepCopy();
    }
}
