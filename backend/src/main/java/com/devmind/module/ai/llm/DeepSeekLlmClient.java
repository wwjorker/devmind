package com.devmind.module.ai.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.devmind.common.api.ResultCode;
import com.devmind.common.exception.BizException;
import com.devmind.module.ai.config.AiProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

@Component
public class DeepSeekLlmClient implements LlmClient {

    private final AiProperties aiProperties;

    public DeepSeekLlmClient(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    @Override
    public boolean supports(String provider) {
        return "deepseek".equalsIgnoreCase(provider);
    }

    @Override
    public LlmResponse generate(LlmRequest request) {
        if (!StringUtils.hasText(aiProperties.getDeepseekApiKey())) {
            throw new BizException(ResultCode.BAD_REQUEST, "DeepSeek API key is not configured");
        }

        RestClient restClient = RestClient.builder()
                .baseUrl(aiProperties.getDeepseekBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + aiProperties.getDeepseekApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        Map<String, Object> body = Map.of(
                "model", aiProperties.getDeepseekModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", "You are DevMind, an assistant that answers strictly from retrieved developer knowledge-base context."),
                        Map.of("role", "user", "content", request.getPrompt())
                ),
                "temperature", aiProperties.getDeepseekTemperature(),
                "stream", false
        );

        try {
            JsonNode response = restClient.post()
                    .uri("/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            String answer = extractAnswer(response);
            JsonNode usage = response.path("usage");
            return new LlmResponse(
                    answer,
                    "deepseek:" + aiProperties.getDeepseekModel(),
                    false,
                    readNullableInt(usage, "prompt_tokens"),
                    readNullableInt(usage, "completion_tokens"),
                    readNullableInt(usage, "total_tokens")
            );
        } catch (RestClientException ex) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "DeepSeek request failed");
        }
    }

    private String extractAnswer(JsonNode response) {
        if (response == null) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "DeepSeek response is empty");
        }

        JsonNode contentNode = response.path("choices")
                .path(0)
                .path("message")
                .path("content");
        if (!contentNode.isTextual() || !StringUtils.hasText(contentNode.asText())) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "DeepSeek response content is empty");
        }
        return contentNode.asText();
    }

    private Integer readNullableInt(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        return value.asInt();
    }
}
