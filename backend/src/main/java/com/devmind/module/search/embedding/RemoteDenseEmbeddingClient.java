package com.devmind.module.search.embedding;

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

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class RemoteDenseEmbeddingClient implements EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(RemoteDenseEmbeddingClient.class);

    private final AiProperties aiProperties;
    private final RestClient.Builder restClientBuilder;

    @Autowired
    public RemoteDenseEmbeddingClient(AiProperties aiProperties, RestClient.Builder restClientBuilder) {
        this.aiProperties = aiProperties;
        this.restClientBuilder = restClientBuilder;
    }

    public RemoteDenseEmbeddingClient(AiProperties aiProperties) {
        this(aiProperties, RestClient.builder());
    }

    @Override
    public String providerName() {
        return "remote-dense";
    }

    @Override
    public Map<String, Double> embed(String text) {
        AiProperties.EmbeddingProperties embedding = aiProperties.getEmbedding();
        AiProperties.RemoteProperties remote = embedding == null ? null : embedding.getRemote();
        if (remote == null || !StringUtils.hasText(remote.getApiKey())) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "external embedding provider is not configured");
        }
        if (!StringUtils.hasText(remote.getBaseUrl()) || !StringUtils.hasText(remote.getModel())) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "external embedding provider is not configured");
        }
        if (!providerName().equalsIgnoreCase(embedding.getProvider())) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "external embedding provider is not enabled");
        }

        RestClient restClient = restClientBuilder.clone()
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + remote.getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        String endpoint = embeddingsEndpoint(remote.getBaseUrl());

        Map<String, Object> body = Map.of(
                "model", remote.getModel(),
                "input", text == null ? "" : text
        );

        try {
            JsonNode response = restClient.post()
                    .uri(endpoint)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            Map<String, Double> vector = extractEmbedding(response);
            warnIfDimensionMismatch(remote, vector.size());
            return vector;
        } catch (RestClientException | IllegalStateException ex) {
            log.warn("Remote embedding request failed. model={}, baseUrl={}",
                    remote.getModel(),
                    remote.getBaseUrl(),
                    ex);
            throw new BizException(ResultCode.INTERNAL_ERROR, "external embedding request failed");
        }
    }

    @Override
    public double cosineSimilarity(Map<String, Double> left, Map<String, Double> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double leftNorm = 0.0;
        double rightNorm = 0.0;
        for (Map.Entry<String, Double> entry : left.entrySet()) {
            double value = entry.getValue();
            leftNorm += value * value;
            dotProduct += value * right.getOrDefault(entry.getKey(), 0.0);
        }
        for (double value : right.values()) {
            rightNorm += value * value;
        }
        if (leftNorm <= 0.0 || rightNorm <= 0.0) {
            return 0.0;
        }
        return Math.max(dotProduct / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm)), 0.0);
    }

    String embeddingsEndpoint(String baseUrl) {
        return baseUrl.replaceAll("/+$", "") + "/embeddings";
    }

    Map<String, Double> extractEmbedding(JsonNode response) {
        JsonNode embeddingNode = response == null
                ? null
                : response.path("data").path(0).path("embedding");
        if (embeddingNode == null || !embeddingNode.isArray()) {
            throw new IllegalStateException("remote embedding response does not contain data[0].embedding");
        }

        Map<String, Double> vector = new LinkedHashMap<>();
        for (int index = 0; index < embeddingNode.size(); index++) {
            JsonNode value = embeddingNode.get(index);
            if (!value.isNumber()) {
                throw new IllegalStateException("remote embedding contains non-numeric value");
            }
            vector.put(String.valueOf(index), value.asDouble());
        }
        if (vector.isEmpty()) {
            throw new IllegalStateException("remote embedding is empty");
        }
        return vector;
    }

    private void warnIfDimensionMismatch(AiProperties.RemoteProperties remote, int actualDimension) {
        Integer expectedDimension = remote.getDimension();
        if (expectedDimension != null && expectedDimension > 0 && expectedDimension != actualDimension) {
            log.warn("Remote embedding dimension mismatch. model={}, baseUrl={}, expectedDimension={}, actualDimension={}",
                    remote.getModel(),
                    remote.getBaseUrl(),
                    expectedDimension,
                    actualDimension);
        }
    }
}
