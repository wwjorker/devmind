package com.devmind.module.ai.service;

import com.devmind.module.search.vo.ChunkSearchResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PromptBuilderServiceTest {

    private final PromptBuilderService promptBuilderService = new PromptBuilderService();

    @Test
    void buildPromptShouldExplainWhenNoChunksWereRetrieved() {
        String prompt = promptBuilderService.buildPrompt("What is cache penetration?", List.of());

        assertThat(prompt)
                .contains("You are DevMind")
                .contains("Answer in the same language as the user's question")
                .contains("Question:")
                .contains("What is cache penetration?")
                .contains("(No relevant chunks were retrieved.)")
                .contains("Answer format:");
    }

    @Test
    void buildPromptShouldIncludeChunkMetadataAndContext() {
        ChunkSearchResponse chunk = new ChunkSearchResponse(
                10L,
                2L,
                "Redis cache penetration review",
                "bug_review",
                "redis,cache",
                0,
                "Cache empty values for a short TTL to protect MySQL from repeated misses.",
                80,
                18
        );

        String prompt = promptBuilderService.buildPrompt("How to handle cache penetration?", List.of(chunk));

        assertThat(prompt)
                .contains("[chunkId=10, documentId=2, title=Redis cache penetration review, score=18]")
                .contains("Cache empty values for a short TTL")
                .contains("Citations: chunk ids used");
    }

    @Test
    void buildPromptShouldUseChineseInstructionsForChineseQuestion() {
        ChunkSearchResponse chunk = new ChunkSearchResponse(
                12L, 4L, "Redis 缓存穿透", "learning_note", "redis", 0,
                "缓存空值可以减少不存在 key 对数据库的重复访问。", 40, 20
        );

        String prompt = promptBuilderService.buildPrompt("如何处理缓存穿透？", List.of(chunk));

        assertThat(prompt)
                .contains("你是 DevMind")
                .contains("问题:")
                .contains("检索上下文:")
                .contains("引用依据：实际使用的 chunkId")
                .doesNotContain("Citations:");
    }

    @Test
    void buildPromptShouldLimitVeryLongChunkContext() {
        ChunkSearchResponse chunk = new ChunkSearchResponse(
                11L,
                3L,
                "Long note",
                "interview_note",
                "java",
                0,
                "a".repeat(5000),
                1000,
                9
        );

        String prompt = promptBuilderService.buildPrompt("Explain this long note.", List.of(chunk));

        assertThat(prompt).contains("a".repeat(600) + "...");
        assertThat(prompt).doesNotContain("a".repeat(700));
        assertThat(prompt).contains("Answer format:");
    }

    @Test
    void buildPromptShouldKeepAllChunksWhilePreviewRemainsBounded() {
        List<ChunkSearchResponse> chunks = List.of(
                longChunk(21L, "first"),
                longChunk(22L, "second"),
                longChunk(23L, "third")
        );

        String prompt = promptBuilderService.buildPrompt("Explain the evidence.", chunks);
        String preview = promptBuilderService.buildPromptPreview(prompt);

        assertThat(prompt)
                .hasSizeGreaterThan(2000)
                .contains("[chunkId=21,")
                .contains("[chunkId=22,")
                .contains("[chunkId=23,")
                .contains("Answer format:");
        assertThat(preview)
                .hasSize(2000)
                .endsWith("...");
        assertThat(prompt).startsWith(preview.substring(0, preview.length() - 3));
    }

    private ChunkSearchResponse longChunk(Long chunkId, String marker) {
        return new ChunkSearchResponse(
                chunkId,
                chunkId + 100,
                marker + " evidence",
                "bug_review",
                "test",
                0,
                marker + " " + "x".repeat(1000),
                1000,
                10
        );
    }
}
