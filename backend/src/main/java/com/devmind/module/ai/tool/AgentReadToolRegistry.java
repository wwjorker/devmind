package com.devmind.module.ai.tool;

import com.devmind.common.api.ResultCode;
import com.devmind.common.exception.BizException;
import com.devmind.module.ai.agent.AgentReadTool;
import com.devmind.module.ai.agent.AgentToolDefinition;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AgentReadToolRegistry {

    private final Map<String, AgentReadTool> tools;

    public AgentReadToolRegistry(SearchKnowledgeReadTool searchKnowledge,
                                 GetChunkEvidenceReadTool getChunkEvidence,
                                 GetAskLogEvidenceReadTool getAskLogEvidence) {
        Map<String, AgentReadTool> byName = new LinkedHashMap<>();
        for (AgentReadTool tool : List.of(
                searchKnowledge, getChunkEvidence, getAskLogEvidence)) {
            String name = tool.definition().name();
            if (byName.putIfAbsent(name, tool) != null) {
                throw new IllegalStateException("duplicate agent tool name: " + name);
            }
        }
        this.tools = Collections.unmodifiableMap(byName);
    }

    public List<AgentToolDefinition> definitions() {
        return tools.values().stream()
                .map(AgentReadTool::definition)
                .toList();
    }

    public AgentReadTool requireAllowed(String name) {
        AgentReadTool tool = tools.get(name);
        if (tool == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "agent tool is not allowed: " + name);
        }
        return tool;
    }
}
