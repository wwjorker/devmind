package com.devmind.module.search.service;

import com.devmind.module.document.entity.DocumentChunk;
import com.devmind.module.document.entity.KnowledgeDocument;
import com.devmind.module.document.mapper.KnowledgeDocumentMapper;
import com.devmind.module.ai.config.AiProperties;
import com.devmind.module.search.embedding.EmbeddingClientRouter;
import com.devmind.module.search.embedding.EmbeddingTextBuilder;
import com.devmind.module.search.embedding.LocalEmbeddingClient;
import com.devmind.module.search.embedding.RemoteDenseEmbeddingClient;
import com.devmind.module.search.entity.DocumentChunkVector;
import com.devmind.module.search.mapper.DocumentChunkVectorMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChunkVectorServiceTest {

    @Test
    void shouldPersistSparseVectorWhenChunksAreRebuilt() {
        DocumentChunkVectorMapper vectorMapper = mock(DocumentChunkVectorMapper.class);
        KnowledgeDocumentMapper documentMapper = mock(KnowledgeDocumentMapper.class);
        ChunkVectorService service = new ChunkVectorService(
                vectorMapper,
                documentMapper,
                localRouter(),
                new EmbeddingTextBuilder(),
                new ObjectMapper()
        );
        when(documentMapper.selectById(100L)).thenReturn(document(100L));

        service.rebuildVectors(1L, 100L, List.of(chunk(10L, 100L)));

        ArgumentCaptor<DocumentChunkVector> captor = ArgumentCaptor.forClass(DocumentChunkVector.class);
        verify(vectorMapper).insert(captor.capture());
        DocumentChunkVector savedVector = captor.getValue();
        assertThat(savedVector.getChunkId()).isEqualTo(10L);
        assertThat(savedVector.getDocumentId()).isEqualTo(100L);
        assertThat(savedVector.getUserId()).isEqualTo(1L);
        assertThat(savedVector.getProviderName()).isEqualTo("local-sparse-vector");
        assertThat(savedVector.getVectorJson()).contains("redis");
        assertThat(savedVector.getStatus()).isEqualTo(1);
    }

    @Test
    void shouldArchiveVectorsByDocument() {
        DocumentChunkVectorMapper vectorMapper = mock(DocumentChunkVectorMapper.class);
        ChunkVectorService service = new ChunkVectorService(
                vectorMapper,
                mock(KnowledgeDocumentMapper.class),
                localRouter(),
                new EmbeddingTextBuilder(),
                new ObjectMapper()
        );

        service.archiveByDocument(1L, 100L);

        verify(vectorMapper).update(any());
    }

    @Test
    void shouldDecodeVectorJson() {
        ChunkVectorService service = new ChunkVectorService(
                mock(DocumentChunkVectorMapper.class),
                mock(KnowledgeDocumentMapper.class),
                localRouter(),
                new EmbeddingTextBuilder(),
                new ObjectMapper()
        );

        Map<String, Double> vector = service.decodeVector("{\"redis\":0.8,\"cache\":0.6}");

        assertThat(vector).containsEntry("redis", 0.8);
        assertThat(vector).containsEntry("cache", 0.6);
    }

    private KnowledgeDocument document(Long id) {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(id);
        document.setUserId(1L);
        document.setTitle("Redis cache penetration review");
        document.setSourceType("bug_review");
        document.setTags("redis,cache");
        document.setStatus(1);
        return document;
    }

    private DocumentChunk chunk(Long id, Long documentId) {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setId(id);
        chunk.setUserId(1L);
        chunk.setDocumentId(documentId);
        chunk.setChunkIndex(0);
        chunk.setContent("Redis cache penetration protects MySQL from repeated misses.");
        chunk.setTokenCount(20);
        chunk.setStatus(1);
        return chunk;
    }

    private EmbeddingClientRouter localRouter() {
        AiProperties aiProperties = new AiProperties();
        return new EmbeddingClientRouter(
                aiProperties,
                List.of(new LocalEmbeddingClient(), new RemoteDenseEmbeddingClient(aiProperties))
        );
    }
}
