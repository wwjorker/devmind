package com.devmind.module.ai.llm;

import com.devmind.common.api.ResultCode;
import com.devmind.common.exception.BizException;
import com.devmind.module.ai.config.AiProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LlmClientRouter {

    private final AiProperties aiProperties;
    private final List<LlmClient> clients;

    public LlmClientRouter(AiProperties aiProperties, List<LlmClient> clients) {
        this.aiProperties = aiProperties;
        this.clients = clients;
    }

    public LlmResponse generate(LlmRequest request) {
        String provider = aiProperties.getProvider();
        return generateWithProvider(provider, request);
    }

    public LlmResponse generateFallbackFromConfiguredProvider(LlmRequest request) {
        String configuredProvider = aiProperties.getProvider();
        if ("mock".equalsIgnoreCase(configuredProvider)) {
            return generateWithProvider(configuredProvider, request);
        }

        LlmResponse fallbackResponse = generateWithProvider("mock", request);
        return new LlmResponse(
                fallbackResponse.getAnswer(),
                configuredProvider + "->" + fallbackResponse.getModelProvider(),
                true,
                fallbackResponse.getPromptTokens(),
                fallbackResponse.getCompletionTokens(),
                fallbackResponse.getTotalTokens()
        );
    }

    private LlmResponse generateWithProvider(String provider, LlmRequest request) {
        return clients.stream()
                .filter(client -> client.supports(provider))
                .findFirst()
                .orElseThrow(() -> new BizException(ResultCode.INTERNAL_ERROR, "unsupported llm provider: " + provider))
                .generate(request);
    }

    public String getConfiguredProvider() {
        return aiProperties.getProvider();
    }
}
