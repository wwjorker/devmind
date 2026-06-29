package com.devmind.module.search.embedding;

import com.devmind.module.document.entity.DocumentChunk;
import com.devmind.module.document.entity.KnowledgeDocument;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingTextBuilderTest {

    private final EmbeddingTextBuilder builder = new EmbeddingTextBuilder();

    @Test
    void shouldBuildChunkEmbeddingTextFromStableDocumentFields() {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setTitle("Redis cache penetration review");
        document.setSourceType("bug_review");
        document.setTags("redis,cache,backend");

        DocumentChunk chunk = new DocumentChunk();
        chunk.setContent("Cache empty values with a short TTL.");

        String embeddingText = builder.buildForChunk(document, chunk);

        assertThat(embeddingText)
                .contains("Redis cache penetration review")
                .contains("bug_review")
                .contains("redis,cache,backend")
                .contains("Cache empty values with a short TTL.");
    }

    @Test
    void shouldSkipBlankValuesAndDeduplicateQueryKeywords() {
        assertThat(builder.buildForChunk(new KnowledgeDocument(), null)).isEmpty();

        String queryText = builder.buildForQuery(List.of(" Redis ", "", "cache", "Redis"));

        assertThat(queryText).isEqualTo("Redis cache");
    }
}
