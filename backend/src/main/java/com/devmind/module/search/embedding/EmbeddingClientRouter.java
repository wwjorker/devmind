package com.devmind.module.search.embedding;

import com.devmind.common.api.ResultCode;
import com.devmind.common.exception.BizException;
import com.devmind.module.ai.config.AiProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

@Component
public class EmbeddingClientRouter {

    private static final String DEFAULT_PROVIDER = "local-sparse-vector";

    private final AiProperties aiProperties;
    private final List<EmbeddingClient> clients;

    public EmbeddingClientRouter(AiProperties aiProperties, List<EmbeddingClient> clients) {
        this.aiProperties = aiProperties;
        this.clients = clients;
    }

    public EmbeddingClient currentClient() {
        String provider = configuredProvider();
        return clients.stream()
                .filter(client -> client.providerName().equalsIgnoreCase(provider))
                .findFirst()
                .orElseThrow(() -> new BizException(ResultCode.INTERNAL_ERROR, "unsupported embedding provider: " + provider));
    }

    public String providerName() {
        return currentClient().providerName();
    }

    public Map<String, Double> embed(String text) {
        return currentClient().embed(text);
    }

    public double cosineSimilarity(Map<String, Double> left, Map<String, Double> right) {
        return currentClient().cosineSimilarity(left, right);
    }

    public String configuredProvider() {
        AiProperties.EmbeddingProperties embedding = aiProperties.getEmbedding();
        if (embedding == null || !StringUtils.hasText(embedding.getProvider())) {
            return DEFAULT_PROVIDER;
        }
        return embedding.getProvider();
    }
}
