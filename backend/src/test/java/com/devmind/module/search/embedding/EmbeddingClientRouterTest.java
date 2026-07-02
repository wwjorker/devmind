package com.devmind.module.search.embedding;

import com.devmind.common.exception.BizException;
import com.devmind.module.ai.config.AiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

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

    @Test
    void remoteDenseEmbeddingClientShouldNotRequestWhenProviderIsNotEnabled() {
        AiProperties aiProperties = remoteEmbeddingProperties();
        aiProperties.getEmbedding().setProvider("local-sparse-vector");
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        RemoteDenseEmbeddingClient client = new RemoteDenseEmbeddingClient(aiProperties, restClientBuilder);

        assertThatThrownBy(() -> client.embed("Redis cache penetration"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("external embedding provider is not enabled");
        server.verify();
    }

    @Test
    void remoteDenseEmbeddingClientShouldPostOpenAiCompatibleRequestAndParseVector() {
        AiProperties aiProperties = remoteEmbeddingProperties();
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        RemoteDenseEmbeddingClient client = new RemoteDenseEmbeddingClient(aiProperties, restClientBuilder);

        server.expect(requestTo("https://embedding.example/embeddings"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
                .andExpect(content().json("""
                        {"model":"test-embedding-model","input":"Redis cache"}
                        """))
                .andRespond(withSuccess("""
                        {"data":[{"embedding":[0.1,-0.2,0.3]}]}
                        """, MediaType.APPLICATION_JSON));

        Map<String, Double> vector = client.embed("Redis cache");

        assertThat(vector)
                .containsEntry("0", 0.1)
                .containsEntry("1", -0.2)
                .containsEntry("2", 0.3);
        server.verify();
    }

    @Test
    void remoteDenseEmbeddingClientShouldPreserveBaseUrlPathWhenPostingEmbeddings() {
        AiProperties aiProperties = remoteEmbeddingProperties();
        aiProperties.getEmbedding().getRemote().setBaseUrl("https://embedding.example/v1");
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        RemoteDenseEmbeddingClient client = new RemoteDenseEmbeddingClient(aiProperties, restClientBuilder);

        server.expect(requestTo("https://embedding.example/v1/embeddings"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"data":[{"embedding":[0.1,-0.2,0.3]}]}
                        """, MediaType.APPLICATION_JSON));

        client.embed("Redis cache");

        server.verify();
    }

    @Test
    void remoteDenseEmbeddingClientShouldParseEmbeddingJsonIntoIndexedVector() throws Exception {
        RemoteDenseEmbeddingClient client = new RemoteDenseEmbeddingClient(new AiProperties());
        ObjectMapper objectMapper = new ObjectMapper();

        Map<String, Double> vector = client.extractEmbedding(objectMapper.readTree("""
                {"data":[{"embedding":[1.0,2.5,-3.25]}]}
                """));

        assertThat(vector)
                .containsExactly(
                        Map.entry("0", 1.0),
                        Map.entry("1", 2.5),
                        Map.entry("2", -3.25)
                );
    }

    private AiProperties remoteEmbeddingProperties() {
        AiProperties aiProperties = new AiProperties();
        aiProperties.getEmbedding().setProvider("remote-dense");
        aiProperties.getEmbedding().getRemote().setBaseUrl("https://embedding.example");
        aiProperties.getEmbedding().getRemote().setApiKey("test-key");
        aiProperties.getEmbedding().getRemote().setModel("test-embedding-model");
        aiProperties.getEmbedding().getRemote().setDimension(3);
        return aiProperties;
    }
}
