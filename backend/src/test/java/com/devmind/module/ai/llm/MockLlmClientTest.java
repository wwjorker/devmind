package com.devmind.module.ai.llm;

import com.devmind.module.ai.vo.CitationResponse;
import com.devmind.module.search.vo.ChunkSearchResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MockLlmClientTest {

    private final MockLlmClient mockLlmClient = new MockLlmClient();

    @Test
    void supportsShouldMatchMockProviderIgnoringCase() {
        assertThat(mockLlmClient.supports("mock")).isTrue();
        assertThat(mockLlmClient.supports("MOCK")).isTrue();
        assertThat(mockLlmClient.supports("deepseek")).isFalse();
    }

    @Test
    void generateShouldReturnFallbackAnswerWhenNoChunksExist() {
        LlmRequest request = new LlmRequest(
                "What is cache penetration?",
                "prompt",
                List.of(),
                List.of()
        );

        LlmResponse response = mockLlmClient.generate(request);

        assertThat(response.isMock()).isTrue();
        assertThat(response.getModelProvider()).isEqualTo("mock-local");
        assertThat(response.getAnswer()).contains("could not find relevant knowledge chunks");
    }

    @Test
    void generateShouldReturnChineseFallbackForChineseQuestion() {
        LlmRequest request = new LlmRequest(
                "缓存穿透是什么？",
                "prompt",
                List.of(),
                List.of()
        );

        LlmResponse response = mockLlmClient.generate(request);

        assertThat(response.getAnswer()).contains("没有检索到相关知识片段");
    }

    @Test
    void generateShouldUseTopChunkAndCitationIds() {
        ChunkSearchResponse chunk = new ChunkSearchResponse(
                7L,
                2L,
                "Redis cache penetration review",
                "bug_review",
                "redis",
                0,
                "Cache null objects for a short TTL and validate invalid parameters early.",
                120,
                20
        );
        CitationResponse citation = new CitationResponse(7L, 2L, "Redis cache penetration review", 0, 20);
        LlmRequest request = new LlmRequest(
                "How to prevent cache penetration?",
                "prompt",
                List.of(chunk),
                List.of(citation)
        );

        LlmResponse response = mockLlmClient.generate(request);

        assertThat(response.getAnswer())
                .contains("How to prevent cache penetration?")
                .contains("Redis cache penetration review")
                .contains("chunkId=7");
    }

    @Test
    void generateShouldUseChineseAnswerForChineseQuestion() {
        ChunkSearchResponse chunk = new ChunkSearchResponse(
                8L,
                3L,
                "Redis 缓存穿透复盘",
                "bug_review",
                "redis",
                0,
                "缓存空值并设置较短 TTL，可以保护 MySQL 免受重复 miss 冲击。",
                60,
                18
        );
        CitationResponse citation = new CitationResponse(8L, 3L, "Redis 缓存穿透复盘", 0, 18);
        LlmRequest request = new LlmRequest(
                "面试中应该如何解释 Redis 缓存穿透？",
                "prompt",
                List.of(chunk),
                List.of(citation)
        );

        LlmResponse response = mockLlmClient.generate(request);

        assertThat(response.getAnswer())
                .contains("这是基于召回知识片段生成的 Mock 回答")
                .contains("面试中应该如何解释 Redis 缓存穿透？")
                .contains("引用来源")
                .contains("chunkId=8");
    }
}
