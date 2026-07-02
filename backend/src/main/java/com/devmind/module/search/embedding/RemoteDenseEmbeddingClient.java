package com.devmind.module.search.embedding;

import com.devmind.common.api.ResultCode;
import com.devmind.common.exception.BizException;
import com.devmind.module.ai.config.AiProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

@Component
public class RemoteDenseEmbeddingClient implements EmbeddingClient {

    private final AiProperties aiProperties;

    public RemoteDenseEmbeddingClient(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
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
        throw new BizException(ResultCode.INTERNAL_ERROR, "external embedding provider http call is not implemented yet");
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
}
