package com.devmind.module.ai.llm;

import com.devmind.common.exception.BizException;
import com.devmind.common.api.ResultCode;
import com.devmind.module.ai.config.AiProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmClientRouterTest {

    @Test
    void generateShouldDelegateToMatchingProvider() {
        AiProperties aiProperties = new AiProperties();
        aiProperties.setProvider("deepseek");
        LlmClientRouter router = new LlmClientRouter(
                aiProperties,
                List.of(new StubClient("mock"), new StubClient("deepseek"))
        );

        LlmResponse response = router.generate(new LlmRequest("question", "prompt", List.of(), List.of()));

        assertThat(response.getModelProvider()).isEqualTo("deepseek-model");
        assertThat(response.getAnswer()).isEqualTo("answer from deepseek");
    }

    @Test
    void generateShouldFailWhenProviderIsUnsupported() {
        AiProperties aiProperties = new AiProperties();
        aiProperties.setProvider("unknown");
        LlmClientRouter router = new LlmClientRouter(aiProperties, List.of(new StubClient("mock")));

        assertThatThrownBy(() -> router.generate(new LlmRequest("question", "prompt", List.of(), List.of())))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("unsupported llm provider: unknown");
    }

    @Test
    void fallbackShouldUseMockWhenConfiguredProviderFails() {
        AiProperties aiProperties = new AiProperties();
        aiProperties.setProvider("deepseek");
        LlmClientRouter router = new LlmClientRouter(
                aiProperties,
                List.of(new FailingClient("deepseek"), new StubClient("mock"))
        );

        LlmResponse response = router.generateFallbackFromConfiguredProvider(
                new LlmRequest("question", "prompt", List.of(), List.of())
        );

        assertThat(response.getModelProvider()).isEqualTo("deepseek->mock-model");
        assertThat(response.getAnswer()).isEqualTo("answer from mock");
        assertThat(response.isMock()).isTrue();
    }

    private static class StubClient implements LlmClient {

        private final String provider;

        private StubClient(String provider) {
            this.provider = provider;
        }

        @Override
        public boolean supports(String provider) {
            return this.provider.equals(provider);
        }

        @Override
        public LlmResponse generate(LlmRequest request) {
            return new LlmResponse("answer from " + provider, provider + "-model", false);
        }
    }

    private static class FailingClient implements LlmClient {

        private final String provider;

        private FailingClient(String provider) {
            this.provider = provider;
        }

        @Override
        public boolean supports(String provider) {
            return this.provider.equals(provider);
        }

        @Override
        public LlmResponse generate(LlmRequest request) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "provider failed");
        }
    }
}
