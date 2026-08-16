package com.devmind.module.ai.agent;

import com.devmind.common.api.ResultCode;
import com.devmind.common.exception.BizException;
import com.devmind.module.ai.config.AiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DeepSeekAgentModelClient implements AgentModelClient {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekAgentModelClient.class);

    private final AiProperties aiProperties;
    private final RestClient.Builder restClientBuilder;

    @Autowired
    public DeepSeekAgentModelClient(AiProperties aiProperties, RestClient.Builder restClientBuilder) {
        this.aiProperties = aiProperties;
        this.restClientBuilder = restClientBuilder;
    }

    public DeepSeekAgentModelClient(AiProperties aiProperties) {
        this(aiProperties, RestClient.builder());
    }

    @Override
    public boolean supports(String provider) {
        return "deepseek".equalsIgnoreCase(provider);
    }

    @Override
    public AgentModelResponse complete(AgentModelRequest request) {
        if (!StringUtils.hasText(aiProperties.getDeepseekApiKey())) {
            throw new BizException(ResultCode.BAD_REQUEST, "DeepSeek API key is not configured");
        }

        RestClient restClient = restClientBuilder.clone()
                .baseUrl(aiProperties.getDeepseekBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + aiProperties.getDeepseekApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        try {
            JsonNode response = restClient.post()
                    .uri("/chat/completions")
                    .body(toWireRequest(request))
                    .retrieve()
                    .body(JsonNode.class);
            return extractResponse(response);
        } catch (RestClientException | IllegalArgumentException | IllegalStateException ex) {
            log.warn("DeepSeek agent-model request failed. model={}, baseUrl={}",
                    aiProperties.getDeepseekModel(),
                    aiProperties.getDeepseekBaseUrl(),
                    ex);
            throw new BizException(ResultCode.INTERNAL_ERROR, "DeepSeek agent-model request failed");
        }
    }

    Map<String, Object> toWireRequest(AgentModelRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", aiProperties.getDeepseekModel());
        body.put("messages", request.messages().stream().map(this::toWireMessage).toList());
        body.put("temperature", aiProperties.getDeepseekTemperature());
        body.put("stream", false);
        body.put("thinking", Map.of("type", "disabled"));
        if (!request.tools().isEmpty()) {
            body.put("tools", request.tools().stream().map(this::toWireTool).toList());
        }
        if (request.toolChoice() != null) {
            body.put("tool_choice", request.toolChoice().toWireValue());
        }
        return body;
    }

    AgentModelResponse extractResponse(JsonNode response) {
        if (response == null) {
            throw new IllegalStateException("DeepSeek response is empty");
        }

        JsonNode choice = response.path("choices").path(0);
        JsonNode message = choice.path("message");
        if (!choice.isObject() || !message.isObject()) {
            throw new IllegalStateException("DeepSeek response has no assistant message");
        }
        JsonNode role = message.path("role");
        if (role.isTextual() && !"assistant".equals(role.asText())) {
            throw new IllegalStateException("DeepSeek response message is not an assistant message");
        }

        String finishReason = requiredText(choice, "finish_reason");
        String content = nullableText(message, "content");
        List<AgentToolCall> toolCalls = readToolCalls(message.path("tool_calls"));
        boolean toolCallFinish = "tool_calls".equals(finishReason);
        if (toolCallFinish != !toolCalls.isEmpty()) {
            throw new IllegalStateException("DeepSeek finish reason and tool calls are inconsistent");
        }
        if (!StringUtils.hasText(content) && toolCalls.isEmpty()) {
            throw new IllegalStateException("DeepSeek assistant message is empty");
        }

        AgentMessage assistantMessage = new AgentMessage(
                AgentMessage.Role.ASSISTANT,
                content,
                toolCalls,
                null
        );
        JsonNode usage = response.path("usage");
        return new AgentModelResponse(
                assistantMessage,
                finishReason,
                "deepseek:" + aiProperties.getDeepseekModel(),
                new AgentTokenUsage(
                        readNullableInt(usage, "prompt_tokens"),
                        readNullableInt(usage, "completion_tokens"),
                        readNullableInt(usage, "total_tokens")
                )
        );
    }

    private Map<String, Object> toWireMessage(AgentMessage message) {
        Map<String, Object> wireMessage = new LinkedHashMap<>();
        wireMessage.put("role", message.role().wireValue());
        wireMessage.put("content", message.content());
        if (!message.toolCalls().isEmpty()) {
            wireMessage.put("tool_calls", message.toolCalls().stream().map(this::toWireToolCall).toList());
        }
        if (message.role() == AgentMessage.Role.TOOL) {
            wireMessage.put("tool_call_id", message.toolCallId());
        }
        return wireMessage;
    }

    private Map<String, Object> toWireToolCall(AgentToolCall toolCall) {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", toolCall.name());
        function.put("arguments", toolCall.arguments());

        Map<String, Object> wireToolCall = new LinkedHashMap<>();
        wireToolCall.put("id", toolCall.id());
        wireToolCall.put("type", toolCall.type());
        wireToolCall.put("function", function);
        return wireToolCall;
    }

    private Map<String, Object> toWireTool(AgentToolDefinition tool) {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", tool.name());
        function.put("description", tool.description());
        function.put("parameters", tool.parameters());

        Map<String, Object> wireTool = new LinkedHashMap<>();
        wireTool.put("type", AgentToolCall.FUNCTION_TYPE);
        wireTool.put("function", function);
        return wireTool;
    }

    private List<AgentToolCall> readToolCalls(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw new IllegalStateException("DeepSeek tool_calls is not an array");
        }
        List<AgentToolCall> calls = new ArrayList<>();
        for (JsonNode item : node) {
            JsonNode function = item.path("function");
            calls.add(new AgentToolCall(
                    requiredText(item, "id"),
                    requiredText(item, "type"),
                    requiredText(function, "name"),
                    requiredText(function, "arguments")
            ));
        }
        return List.copyOf(calls);
    }

    private String requiredText(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (!value.isTextual() || !StringUtils.hasText(value.asText())) {
            throw new IllegalStateException("DeepSeek field is missing or blank: " + fieldName);
        }
        return value.asText();
    }

    private String nullableText(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw new IllegalStateException("DeepSeek field is not text: " + fieldName);
        }
        return value.asText();
    }

    private Integer readNullableInt(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (!value.canConvertToInt()) {
            throw new IllegalStateException("DeepSeek token usage is not an integer: " + fieldName);
        }
        return value.asInt();
    }
}
