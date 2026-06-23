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
        return clients.stream()
                .filter(client -> client.supports(provider))
                .findFirst()
                .orElseThrow(() -> new BizException(ResultCode.INTERNAL_ERROR, "unsupported llm provider: " + provider))
                .generate(request);
    }
}
