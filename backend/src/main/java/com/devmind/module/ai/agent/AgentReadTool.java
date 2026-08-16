package com.devmind.module.ai.agent;

import com.fasterxml.jackson.databind.JsonNode;

public interface AgentReadTool {

    AgentToolDefinition definition();

    JsonNode execute(AgentToolContext context, JsonNode arguments);
}
