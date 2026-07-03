package com.devmind.module.search.rerank;

import com.devmind.common.exception.BizException;
import com.devmind.module.ai.config.AiProperties;
import com.devmind.module.search.vo.ChunkSearchResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RerankClientRouterTest {

    @Test
    void shouldResolveNoneRerankClientByDefault() {
        AiProperties aiProperties = new AiProperties();
        RerankClientRouter router = new RerankClientRouter(
                aiProperties,
                List.of(new NoneRerankClient(), new RemoteRerankClient(aiProperties))
        );

        assertThat(router.currentClient()).isInstanceOf(NoneRerankClient.class);
        assertThat(router.configuredProvider()).isEqualTo("none");
    }

    @Test
    void noneRerankClientShouldKeepOriginalOrderAndLimitTopN() {
        NoneRerankClient client = new NoneRerankClient();
        List<ChunkSearchResponse> candidates = List.of(
                chunk(1L, "first"),
                chunk(2L, "second"),
                chunk(3L, "third")
        );

        List<ChunkSearchResponse> reranked = client.rerank("query", candidates, 2);

        assertThat(reranked)
                .extracting(ChunkSearchResponse::getChunkId)
                .containsExactly(1L, 2L);
    }

    @Test
    void shouldResolveRemoteRerankClientWhenConfigured() {
        AiProperties aiProperties = new AiProperties();
        aiProperties.getRerank().setProvider("remote-rerank");
        RemoteRerankClient remoteClient = new RemoteRerankClient(aiProperties);
        RerankClientRouter router = new RerankClientRouter(
                aiProperties,
                List.of(new NoneRerankClient(), remoteClient)
        );

        assertThat(router.currentClient()).isSameAs(remoteClient);
        assertThat(router.clientFor("remote-rerank")).isSameAs(remoteClient);
    }

    @Test
    void remoteRerankClientShouldFailFastWhenNotConfigured() {
        RemoteRerankClient client = new RemoteRerankClient(new AiProperties());

        assertThatThrownBy(() -> client.rerank("query", List.of(chunk(1L, "first")), 1))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("rerank provider is not configured");
    }

    @Test
    void remoteRerankClientShouldPostRequestAndSortByRelevanceScore() {
        AiProperties aiProperties = remoteRerankProperties();
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        RemoteRerankClient client = new RemoteRerankClient(aiProperties, restClientBuilder);
        List<ChunkSearchResponse> candidates = List.of(
                chunk(1L, "low score"),
                chunk(2L, "high score"),
                chunk(3L, "middle score")
        );

        server.expect(requestTo("https://rerank.example/v1/rerank"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer rerank-key"))
                .andExpect(content().json("""
                        {
                          "model":"test-rerank-model",
                          "query":"Redis query",
                          "documents":["low score","high score","middle score"],
                          "top_n":2
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "results":[
                            {"index":0,"relevance_score":0.1},
                            {"index":2,"relevance_score":0.6},
                            {"index":1,"relevance_score":0.9}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<ChunkSearchResponse> reranked = client.rerank("Redis query", candidates, 2);

        assertThat(reranked)
                .extracting(ChunkSearchResponse::getChunkId)
                .containsExactly(2L, 3L);
        server.verify();
    }

    private AiProperties remoteRerankProperties() {
        AiProperties aiProperties = new AiProperties();
        aiProperties.getRerank().setProvider("remote-rerank");
        aiProperties.getRerank().getRemote().setBaseUrl("https://rerank.example/v1");
        aiProperties.getRerank().getRemote().setApiKey("rerank-key");
        aiProperties.getRerank().getRemote().setModel("test-rerank-model");
        return aiProperties;
    }

    private ChunkSearchResponse chunk(Long chunkId, String content) {
        return new ChunkSearchResponse(
                chunkId,
                chunkId,
                "doc-" + chunkId,
                "demo_note",
                "tag",
                0,
                content,
                20,
                10
        );
    }
}
