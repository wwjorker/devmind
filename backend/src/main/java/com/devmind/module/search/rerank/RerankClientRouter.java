package com.devmind.module.search.rerank;

import com.devmind.common.api.ResultCode;
import com.devmind.common.exception.BizException;
import com.devmind.module.ai.config.AiProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
public class RerankClientRouter {

    private static final String DEFAULT_PROVIDER = "none";

    private final AiProperties aiProperties;
    private final List<RerankClient> clients;

    public RerankClientRouter(AiProperties aiProperties, List<RerankClient> clients) {
        this.aiProperties = aiProperties;
        this.clients = clients;
    }

    public RerankClient currentClient() {
        return clientFor(configuredProvider());
    }

    public RerankClient clientFor(String provider) {
        return clients.stream()
                .filter(client -> client.rerankName().equalsIgnoreCase(provider))
                .findFirst()
                .orElseThrow(() -> new BizException(
                        ResultCode.INTERNAL_ERROR,
                        "unsupported rerank provider: " + provider
                ));
    }

    public String configuredProvider() {
        AiProperties.RerankProperties rerank = aiProperties.getRerank();
        if (rerank == null || !StringUtils.hasText(rerank.getProvider())) {
            return DEFAULT_PROVIDER;
        }
        return rerank.getProvider();
    }
}
