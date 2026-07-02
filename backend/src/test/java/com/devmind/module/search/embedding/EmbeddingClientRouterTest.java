package com.devmind.module.search.embedding;

import com.devmind.common.exception.BizException;
import com.devmind.module.ai.config.AiProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmbeddingClientRouterTest {

    @Test
    void shouldResolveLocalEmbeddingClientByDefault() {
        AiProperties aiProperties = new AiProperties();
        EmbeddingClientRouter router = new EmbeddingClientRouter(
                aiProperties,
                List.of(new LocalEmbeddingClient(), new RemoteDenseEmbeddingClient(aiProperties))
        );

        assertThat(router.currentClient()).isInstanceOf(LocalEmbeddingClient.class);
        assertThat(router.providerName()).isEqualTo("local-sparse-vector");
    }

    @Test
    void shouldResolveRemoteDenseEmbeddingClientWhenConfigured() {
        AiProperties aiProperties = new AiProperties();
        aiProperties.getEmbedding().setProvider("remote-dense");
        EmbeddingClientRouter router = new EmbeddingClientRouter(
                aiProperties,
                List.of(new LocalEmbeddingClient(), new RemoteDenseEmbeddingClient(aiProperties))
        );

        assertThat(router.currentClient()).isInstanceOf(RemoteDenseEmbeddingClient.class);
        assertThat(router.providerName()).isEqualTo("remote-dense");
    }

    @Test
    void remoteDenseEmbeddingClientShouldFailFastWhenApiKeyIsEmpty() {
        AiProperties aiProperties = new AiProperties();
        RemoteDenseEmbeddingClient client = new RemoteDenseEmbeddingClient(aiProperties);

        assertThatThrownBy(() -> client.embed("Redis cache penetration"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("external embedding provider is not configured");
    }
}
