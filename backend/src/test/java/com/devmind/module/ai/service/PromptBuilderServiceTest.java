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
}
