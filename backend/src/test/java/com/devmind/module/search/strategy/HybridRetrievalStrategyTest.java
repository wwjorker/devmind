package com.devmind.module.search.strategy;

import com.devmind.module.document.entity.DocumentChunk;
import com.devmind.module.document.entity.KnowledgeDocument;
import com.devmind.module.document.mapper.DocumentChunkMapper;
import com.devmind.module.document.mapper.KnowledgeDocumentMapper;
import com.devmind.module.search.embedding.EmbeddingTextBuilder;
import com.devmind.module.search.embedding.LocalEmbeddingClient;
import com.devmind.module.search.vo.ChunkSearchResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HybridRetrievalStrategyTest {

    @Test
    void shouldMergeKeywordAndLocalEmbeddingScores() {
        KeywordRetrievalStrategy keywordStrategy = mock(KeywordRetrievalStrategy.class);
        DocumentChunkMapper chunkMapper = mock(DocumentChunkMapper.class);
        KnowledgeDocumentMapper documentMapper = mock(KnowledgeDocumentMapper.class);
        HybridRetrievalStrategy strategy = new HybridRetrievalStrategy(
                keywordStrategy,
                chunkMapper,
                documentMapper,
                new LocalEmbeddingClient(),
                new EmbeddingTextBuilder()
        );

        when(keywordStrategy.retrieve(eq(1L), eq(List.of("Redis", "cache")), eq(6)))
                .thenReturn(List.of(new ChunkSearchResponse(
                        10L,
                        100L,
                        "Redis cache penetration review",
                        "bug_review",
                        "redis,cache",
                        0,
                        "Redis cache penetration protects MySQL from repeated misses.",
                        20,
                        30
                )));
        when(chunkMapper.selectList(any())).thenReturn(List.of(
                chunk(10L, 100L, "Redis cache penetration can repeatedly hit MySQL for missing keys."),
                chunk(11L, 101L, "Flyway migration manages database schema versions.")
        ));
        when(documentMapper.selectList(any())).thenReturn(List.of(
                document(100L, "Redis cache penetration review", "redis,cache"),
                document(101L, "Flyway migration database change", "flyway,database")
        ));

        List<ChunkSearchResponse> responses = strategy.retrieve(1L, List.of("Redis", "cache"), 2);

        assertThat(strategy.strategyName()).isEqualTo("hybrid-keyword-local-embedding-v1");
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getChunkId()).isEqualTo(10L);
        assertThat(responses.get(0).getScore()).isGreaterThan(30);
    }

    @Test
    void shouldReturnVectorOnlyMatchWhenKeywordBaselineMisses() {
        KeywordRetrievalStrategy keywordStrategy = mock(KeywordRetrievalStrategy.class);
        DocumentChunkMapper chunkMapper = mock(DocumentChunkMapper.class);
        KnowledgeDocumentMapper documentMapper = mock(KnowledgeDocumentMapper.class);
        HybridRetrievalStrategy strategy = new HybridRetrievalStrategy(
                keywordStrategy,
                chunkMapper,
                documentMapper,
                new LocalEmbeddingClient(),
                new EmbeddingTextBuilder()
        );

        when(keywordStrategy.retrieve(eq(1L), eq(List.of("penetration")), eq(3))).thenReturn(List.of());
        when(chunkMapper.selectList(any())).thenReturn(List.of(
                chunk(20L, 200L, "Cache penetration should cache empty values and limit abnormal traffic.")
        ));
        when(documentMapper.selectList(any())).thenReturn(List.of(
                document(200L, "Redis cache penetration review", "redis,cache")
        ));

        List<ChunkSearchResponse> responses = strategy.retrieve(1L, List.of("penetration"), 1);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getChunkId()).isEqualTo(20L);
        assertThat(responses.get(0).getScore()).isPositive();
    }

    private DocumentChunk chunk(Long id, Long documentId, String content) {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setId(id);
        chunk.setUserId(1L);
        chunk.setDocumentId(documentId);
        chunk.setChunkIndex(0);
        chunk.setContent(content);
        chunk.setTokenCount(20);
        chunk.setStatus(1);
        return chunk;
    }

    private KnowledgeDocument document(Long id, String title, String tags) {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(id);
        document.setUserId(1L);
        document.setTitle(title);
        document.setSourceType("java_note");
        document.setTags(tags);
        document.setStatus(1);
        return document;
    }
}
