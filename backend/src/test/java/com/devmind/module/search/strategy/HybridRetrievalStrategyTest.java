package com.devmind.module.search.strategy;

import com.devmind.module.document.entity.DocumentChunk;
import com.devmind.module.document.entity.KnowledgeDocument;
import com.devmind.module.document.mapper.DocumentChunkMapper;
import com.devmind.module.document.mapper.KnowledgeDocumentMapper;
import com.devmind.module.ai.config.AiProperties;
import com.devmind.module.search.embedding.EmbeddingClient;
import com.devmind.module.search.embedding.EmbeddingClientRouter;
import com.devmind.module.search.embedding.EmbeddingTextBuilder;
import com.devmind.module.search.embedding.LocalEmbeddingClient;
import com.devmind.module.search.embedding.RemoteDenseEmbeddingClient;
import com.devmind.module.search.entity.DocumentChunkVector;
import com.devmind.module.search.service.ChunkVectorService;
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
    void shouldFuseKeywordAndLocalSparseVectorRanks() {
        KeywordRetrievalStrategy keywordStrategy = mock(KeywordRetrievalStrategy.class);
        DocumentChunkMapper chunkMapper = mock(DocumentChunkMapper.class);
        KnowledgeDocumentMapper documentMapper = mock(KnowledgeDocumentMapper.class);
        EmbeddingClient embeddingClient = new LocalEmbeddingClient();
        ChunkVectorService chunkVectorService = mock(ChunkVectorService.class);
        HybridRetrievalStrategy strategy = new HybridRetrievalStrategy(
                keywordStrategy,
                chunkMapper,
                documentMapper,
                localRouter(),
                new EmbeddingTextBuilder(),
                chunkVectorService
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
        when(chunkVectorService.listActiveVectors(eq(1L), eq(120))).thenReturn(List.of(vector(10L, 100L)));
        when(chunkVectorService.decodeVector(any())).thenReturn(embeddingClient.embed("Redis cache penetration MySQL"));
        when(chunkMapper.selectList(any())).thenReturn(List.of(
                chunk(10L, 100L, "Redis cache penetration can repeatedly hit MySQL for missing keys.")
        ));
        when(documentMapper.selectList(any())).thenReturn(List.of(
                document(100L, "Redis cache penetration review", "redis,cache")
        ));

        List<ChunkSearchResponse> responses = strategy.retrieve(1L, List.of("Redis", "cache"), 2);

        assertThat(strategy.strategyName()).isEqualTo("hybrid-keyword-local-sparse-vector-rrf-v1");
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getChunkId()).isEqualTo(10L);
        assertThat(responses.get(0).getScore()).isGreaterThan(30);
    }

    @Test
    void shouldPromoteCandidateWhenKeywordAndVectorRanksAgree() {
        KeywordRetrievalStrategy keywordStrategy = mock(KeywordRetrievalStrategy.class);
        DocumentChunkMapper chunkMapper = mock(DocumentChunkMapper.class);
        KnowledgeDocumentMapper documentMapper = mock(KnowledgeDocumentMapper.class);
        EmbeddingClient embeddingClient = new LocalEmbeddingClient();
        ChunkVectorService chunkVectorService = mock(ChunkVectorService.class);
        HybridRetrievalStrategy strategy = new HybridRetrievalStrategy(
                keywordStrategy,
                chunkMapper,
                documentMapper,
                localRouter(),
                new EmbeddingTextBuilder(),
                chunkVectorService
        );

        when(keywordStrategy.retrieve(eq(1L), eq(List.of("Redis")), eq(6))).thenReturn(List.of(
                new ChunkSearchResponse(
                        10L,
                        100L,
                        "Generic Redis note",
                        "java_note",
                        "redis",
                        0,
                        "Redis stores cache data.",
                        20,
                        200
                ),
                new ChunkSearchResponse(
                        20L,
                        200L,
                        "Redis cache penetration review",
                        "bug_review",
                        "redis,cache",
                        0,
                        "Redis cache penetration protects MySQL from repeated misses.",
                        20,
                        50
                )
        ));
        when(chunkVectorService.listActiveVectors(eq(1L), eq(120))).thenReturn(List.of(vector(20L, 200L)));
        when(chunkVectorService.decodeVector(any())).thenReturn(embeddingClient.embed("Redis cache penetration MySQL"));
        when(chunkMapper.selectList(any())).thenReturn(List.of(
                chunk(20L, 200L, "Redis cache penetration can repeatedly hit MySQL for missing keys.")
        ));
        when(documentMapper.selectList(any())).thenReturn(List.of(
                document(200L, "Redis cache penetration review", "redis,cache")
        ));

        List<ChunkSearchResponse> responses = strategy.retrieve(1L, List.of("Redis"), 2);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getChunkId()).isEqualTo(20L);
        assertThat(responses.get(1).getChunkId()).isEqualTo(10L);
    }

    @Test
    void shouldReturnVectorOnlyMatchWhenKeywordBaselineMisses() {
        KeywordRetrievalStrategy keywordStrategy = mock(KeywordRetrievalStrategy.class);
        DocumentChunkMapper chunkMapper = mock(DocumentChunkMapper.class);
        KnowledgeDocumentMapper documentMapper = mock(KnowledgeDocumentMapper.class);
        ChunkVectorService chunkVectorService = mock(ChunkVectorService.class);
        HybridRetrievalStrategy strategy = new HybridRetrievalStrategy(
                keywordStrategy,
                chunkMapper,
                documentMapper,
                localRouter(),
                new EmbeddingTextBuilder(),
                chunkVectorService
        );

        when(keywordStrategy.retrieve(eq(1L), eq(List.of("penetration")), eq(3))).thenReturn(List.of());
        when(chunkVectorService.listActiveVectors(eq(1L), eq(120))).thenReturn(List.of());
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

    @Test
    void shouldRankPersistedSparseVectorsBySimilarityBeforeRrf() {
        KeywordRetrievalStrategy keywordStrategy = mock(KeywordRetrievalStrategy.class);
        DocumentChunkMapper chunkMapper = mock(DocumentChunkMapper.class);
        KnowledgeDocumentMapper documentMapper = mock(KnowledgeDocumentMapper.class);
        EmbeddingClient embeddingClient = new LocalEmbeddingClient();
        ChunkVectorService chunkVectorService = mock(ChunkVectorService.class);
        HybridRetrievalStrategy strategy = new HybridRetrievalStrategy(
                keywordStrategy,
                chunkMapper,
                documentMapper,
                localRouter(),
                new EmbeddingTextBuilder(),
                chunkVectorService
        );

        when(keywordStrategy.retrieve(eq(1L), eq(List.of("cache", "penetration")), eq(6))).thenReturn(List.of());
        when(chunkVectorService.listActiveVectors(eq(1L), eq(120))).thenReturn(List.of(
                vector(10L, 100L, "generic-vector"),
                vector(20L, 200L, "penetration-vector")
        ));
        when(chunkVectorService.decodeVector(eq("generic-vector")))
                .thenReturn(embeddingClient.embed("generic cache storage note"));
        when(chunkVectorService.decodeVector(eq("penetration-vector")))
                .thenReturn(embeddingClient.embed("cache penetration empty value ttl MySQL"));
        when(chunkMapper.selectList(any())).thenReturn(List.of(
                chunk(10L, 100L, "Redis stores cache data."),
                chunk(20L, 200L, "Cache penetration should cache empty values and protect MySQL.")
        ));
        when(documentMapper.selectList(any())).thenReturn(List.of(
                document(100L, "Generic Redis note", "redis"),
                document(200L, "Redis cache penetration review", "redis,cache")
        ));

        List<ChunkSearchResponse> responses = strategy.retrieve(1L, List.of("cache", "penetration"), 2);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getChunkId()).isEqualTo(20L);
        assertThat(responses.get(1).getChunkId()).isEqualTo(10L);
        assertThat(responses.get(0).getScore()).isGreaterThan(responses.get(1).getScore());
    }

    private DocumentChunkVector vector(Long chunkId, Long documentId) {
        return vector(chunkId, documentId, "{}");
    }

    private DocumentChunkVector vector(Long chunkId, Long documentId, String vectorJson) {
        DocumentChunkVector vector = new DocumentChunkVector();
        vector.setId(chunkId + 1000);
        vector.setChunkId(chunkId);
        vector.setDocumentId(documentId);
        vector.setUserId(1L);
        vector.setProviderName("local-sparse-vector");
        vector.setVectorJson(vectorJson);
        vector.setStatus(1);
        return vector;
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

    private EmbeddingClientRouter localRouter() {
        AiProperties aiProperties = new AiProperties();
        return new EmbeddingClientRouter(
                aiProperties,
                List.of(new LocalEmbeddingClient(), new RemoteDenseEmbeddingClient(aiProperties))
        );
    }
}
