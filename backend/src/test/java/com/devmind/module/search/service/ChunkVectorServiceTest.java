package com.devmind.module.search.service;

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
import com.devmind.module.search.mapper.DocumentChunkVectorMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.devmind.module.search.vectorstore.PgVectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChunkVectorServiceTest {

    @Test
    void shouldPersistSparseVectorWhenChunksAreRebuilt() {
        DocumentChunkVectorMapper vectorMapper = mock(DocumentChunkVectorMapper.class);
        KnowledgeDocumentMapper documentMapper = mock(KnowledgeDocumentMapper.class);
        ChunkVectorService service = new ChunkVectorService(
                vectorMapper,
                mock(DocumentChunkMapper.class),
                documentMapper,
                localRouter(),
                new EmbeddingTextBuilder(),
                new ObjectMapper(),
                emptyPgVectorStoreProvider()
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
    void shouldArchiveVectorsForAllProvidersByDocument() {
        DocumentChunkVectorMapper vectorMapper = mock(DocumentChunkVectorMapper.class);
        DocumentChunkVector localVector = vector(10L, 100L, "local-sparse-vector");
        DocumentChunkVector remoteVector = vector(10L, 100L, "remote-dense");
        ChunkVectorService service = new ChunkVectorService(
                vectorMapper,
                mock(DocumentChunkMapper.class),
                mock(KnowledgeDocumentMapper.class),
                localRouter(),
                new EmbeddingTextBuilder(),
                new ObjectMapper(),
                emptyPgVectorStoreProvider()
        );
        when(vectorMapper.selectList(any())).thenReturn(List.of(localVector, remoteVector));

        service.archiveByDocument(1L, 100L);

        ArgumentCaptor<DocumentChunkVector> captor = ArgumentCaptor.forClass(DocumentChunkVector.class);
        verify(vectorMapper, times(2)).updateById(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(DocumentChunkVector::getProviderName)
                .containsExactlyInAnyOrder("local-sparse-vector", "remote-dense");
        assertThat(captor.getAllValues())
                .extracting(DocumentChunkVector::getStatus)
                .containsOnly(0);
    }

    @Test
    void shouldDecodeVectorJson() {
        ChunkVectorService service = new ChunkVectorService(
                mock(DocumentChunkVectorMapper.class),
                mock(DocumentChunkMapper.class),
                mock(KnowledgeDocumentMapper.class),
                localRouter(),
                new EmbeddingTextBuilder(),
                new ObjectMapper(),
                emptyPgVectorStoreProvider()
        );

        Map<String, Double> vector = service.decodeVector("{\"redis\":0.8,\"cache\":0.6}");

        assertThat(vector).containsEntry("redis", 0.8);
        assertThat(vector).containsEntry("cache", 0.6);
    }

    @Test
    void shouldBackfillMissingLocalSparseVectorsAndSkipExistingOnes() {
        DocumentChunkVectorMapper vectorMapper = mock(DocumentChunkVectorMapper.class);
        DocumentChunkMapper chunkMapper = mock(DocumentChunkMapper.class);
        KnowledgeDocumentMapper documentMapper = mock(KnowledgeDocumentMapper.class);
        ChunkVectorService service = new ChunkVectorService(
                vectorMapper,
                chunkMapper,
                documentMapper,
                localRouter(),
                new EmbeddingTextBuilder(),
                new ObjectMapper(),
                emptyPgVectorStoreProvider()
        );
        when(chunkMapper.selectList(any())).thenReturn(List.of(
                chunk(10L, 100L),
                chunk(20L, 100L)
        ));
        when(vectorMapper.selectList(any())).thenReturn(List.of(vector(10L, 100L, "local-sparse-vector")));
        when(documentMapper.selectList(any())).thenReturn(List.of(document(100L)));
        when(vectorMapper.selectOne(any())).thenReturn(null);

        service.backfillVectors(1L, "local-sparse-vector");

        ArgumentCaptor<DocumentChunkVector> captor = ArgumentCaptor.forClass(DocumentChunkVector.class);
        verify(vectorMapper).insert(captor.capture());
        DocumentChunkVector savedVector = captor.getValue();
        assertThat(savedVector.getChunkId()).isEqualTo(20L);
        assertThat(savedVector.getProviderName()).isEqualTo("local-sparse-vector");
        assertThat(savedVector.getVectorJson()).contains("redis");
    }

    @Test
    void shouldDoubleWriteRevivedArchivedVectorToPgVectorDuringBackfill() {
        DocumentChunkVectorMapper vectorMapper = mock(DocumentChunkVectorMapper.class);
        DocumentChunkMapper chunkMapper = mock(DocumentChunkMapper.class);
        KnowledgeDocumentMapper documentMapper = mock(KnowledgeDocumentMapper.class);
        EmbeddingClientRouter embeddingClientRouter = mock(EmbeddingClientRouter.class);
        EmbeddingClient denseClient = mock(EmbeddingClient.class);
        PgVectorStore pgVectorStore = mock(PgVectorStore.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<PgVectorStore> pgVectorStoreProvider = mock(ObjectProvider.class);
        when(pgVectorStoreProvider.getIfAvailable()).thenReturn(pgVectorStore);
        when(pgVectorStore.dimension()).thenReturn(3);
        when(embeddingClientRouter.clientFor("remote-dense")).thenReturn(denseClient);
        when(denseClient.providerName()).thenReturn("remote-dense");
        when(denseClient.embed(any())).thenReturn(Map.of("0", 0.1, "1", 0.2, "2", 0.3));
        ChunkVectorService service = new ChunkVectorService(
                vectorMapper,
                chunkMapper,
                documentMapper,
                embeddingClientRouter,
                new EmbeddingTextBuilder(),
                new ObjectMapper(),
                pgVectorStoreProvider
        );
        when(chunkMapper.selectList(any())).thenReturn(List.of(chunk(10L, 100L)));
        when(vectorMapper.selectList(any())).thenReturn(List.of());
        when(documentMapper.selectList(any())).thenReturn(List.of(document(100L)));
        DocumentChunkVector archivedVector = vector(10L, 100L, "remote-dense");
        archivedVector.setStatus(0);
        when(vectorMapper.selectOne(any())).thenReturn(archivedVector);

        service.backfillVectors(1L, "remote-dense");

        ArgumentCaptor<DocumentChunkVector> revivedCaptor = ArgumentCaptor.forClass(DocumentChunkVector.class);
        verify(vectorMapper).updateById(revivedCaptor.capture());
        assertThat(revivedCaptor.getValue().getStatus()).isEqualTo(1);
        verify(vectorMapper, never()).insert(any(DocumentChunkVector.class));
        ArgumentCaptor<float[]> embeddingCaptor = ArgumentCaptor.forClass(float[].class);
        verify(pgVectorStore).upsertChunkVector(
                eq(1L),
                eq(100L),
                eq(10L),
                eq("remote-dense"),
                embeddingCaptor.capture()
        );
        assertThat(embeddingCaptor.getValue()).containsExactly(0.1f, 0.2f, 0.3f);
    }

    @Test
    void shouldReplayActiveDenseVectorToPgVectorWithoutReembeddingOrUpdatingMySql() {
        DocumentChunkVectorMapper vectorMapper = mock(DocumentChunkVectorMapper.class);
        DocumentChunkMapper chunkMapper = mock(DocumentChunkMapper.class);
        KnowledgeDocumentMapper documentMapper = mock(KnowledgeDocumentMapper.class);
        EmbeddingClientRouter embeddingClientRouter = mock(EmbeddingClientRouter.class);
        EmbeddingClient denseClient = mock(EmbeddingClient.class);
        PgVectorStore pgVectorStore = mock(PgVectorStore.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<PgVectorStore> pgVectorStoreProvider = mock(ObjectProvider.class);
        when(pgVectorStoreProvider.getIfAvailable()).thenReturn(pgVectorStore);
        when(pgVectorStore.dimension()).thenReturn(3);
        when(embeddingClientRouter.clientFor("remote-dense")).thenReturn(denseClient);
        when(denseClient.providerName()).thenReturn("remote-dense");
        ChunkVectorService service = new ChunkVectorService(
                vectorMapper,
                chunkMapper,
                documentMapper,
                embeddingClientRouter,
                new EmbeddingTextBuilder(),
                new ObjectMapper(),
                pgVectorStoreProvider
        );
        DocumentChunkVector activeVector = vector(10L, 100L, "remote-dense");
        activeVector.setVectorJson("{\"0\":0.1,\"1\":0.2,\"2\":0.3}");
        when(chunkMapper.selectList(any())).thenReturn(List.of(chunk(10L, 100L)));
        when(vectorMapper.selectList(any())).thenReturn(List.of(activeVector));
        when(documentMapper.selectList(any())).thenReturn(List.of(document(100L)));

        service.backfillVectors(1L, "remote-dense");

        verify(denseClient, never()).embed(any());
        verify(vectorMapper, never()).insert(any(DocumentChunkVector.class));
        verify(vectorMapper, never()).updateById(any(DocumentChunkVector.class));
        ArgumentCaptor<float[]> embeddingCaptor = ArgumentCaptor.forClass(float[].class);
        verify(pgVectorStore).upsertChunkVector(
                eq(1L),
                eq(100L),
                eq(10L),
                eq("remote-dense"),
                embeddingCaptor.capture()
        );
        assertThat(embeddingCaptor.getValue()).containsExactly(0.1f, 0.2f, 0.3f);
    }

    @Test
    void shouldRepairFailedPgVectorDoubleWriteOnLaterBackfill() {
        DocumentChunkVectorMapper vectorMapper = mock(DocumentChunkVectorMapper.class);
        DocumentChunkMapper chunkMapper = mock(DocumentChunkMapper.class);
        KnowledgeDocumentMapper documentMapper = mock(KnowledgeDocumentMapper.class);
        EmbeddingClientRouter embeddingClientRouter = mock(EmbeddingClientRouter.class);
        EmbeddingClient denseClient = mock(EmbeddingClient.class);
        PgVectorStore pgVectorStore = mock(PgVectorStore.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<PgVectorStore> pgVectorStoreProvider = mock(ObjectProvider.class);
        when(pgVectorStoreProvider.getIfAvailable()).thenReturn(pgVectorStore);
        when(pgVectorStore.dimension()).thenReturn(3);
        when(embeddingClientRouter.providerName()).thenReturn("remote-dense");
        when(embeddingClientRouter.embed(any())).thenReturn(Map.of("0", 0.1, "1", 0.2, "2", 0.3));
        when(embeddingClientRouter.clientFor("remote-dense")).thenReturn(denseClient);
        when(denseClient.providerName()).thenReturn("remote-dense");
        doThrow(new RuntimeException("pgvector unavailable"))
                .doNothing()
                .when(pgVectorStore)
                .upsertChunkVector(eq(1L), eq(100L), eq(10L), eq("remote-dense"), any(float[].class));
        ChunkVectorService service = new ChunkVectorService(
                vectorMapper,
                chunkMapper,
                documentMapper,
                embeddingClientRouter,
                new EmbeddingTextBuilder(),
                new ObjectMapper(),
                pgVectorStoreProvider
        );
        when(documentMapper.selectById(100L)).thenReturn(document(100L));

        service.rebuildVectors(1L, 100L, List.of(chunk(10L, 100L)));

        DocumentChunkVector activeVector = vector(10L, 100L, "remote-dense");
        activeVector.setVectorJson("{\"0\":0.1,\"1\":0.2,\"2\":0.3}");
        when(chunkMapper.selectList(any())).thenReturn(List.of(chunk(10L, 100L)));
        when(vectorMapper.selectList(any())).thenReturn(List.of(activeVector));
        when(documentMapper.selectList(any())).thenReturn(List.of(document(100L)));

        service.backfillVectors(1L, "remote-dense");

        verify(vectorMapper, times(1)).insert(any(DocumentChunkVector.class));
        verify(vectorMapper, never()).updateById(any(DocumentChunkVector.class));
        verify(pgVectorStore, times(2)).upsertChunkVector(
                eq(1L), eq(100L), eq(10L), eq("remote-dense"), any(float[].class)
        );
    }

    @Test
    void shouldFailFastWhenBackfillingRemoteDenseWithoutApiKey() {
        DocumentChunkVectorMapper vectorMapper = mock(DocumentChunkVectorMapper.class);
        DocumentChunkMapper chunkMapper = mock(DocumentChunkMapper.class);
        KnowledgeDocumentMapper documentMapper = mock(KnowledgeDocumentMapper.class);
        ChunkVectorService service = new ChunkVectorService(
                vectorMapper,
                chunkMapper,
                documentMapper,
                localRouter(),
                new EmbeddingTextBuilder(),
                new ObjectMapper(),
                emptyPgVectorStoreProvider()
        );
        when(chunkMapper.selectList(any())).thenReturn(List.of(chunk(10L, 100L)));
        when(vectorMapper.selectList(any())).thenReturn(List.of());
        when(documentMapper.selectList(any())).thenReturn(List.of(document(100L)));

        assertThatThrownBy(() -> service.backfillVectors(1L, "remote-dense"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("external embedding provider is not configured");
        verify(vectorMapper, never()).insert(any(DocumentChunkVector.class));
        verify(vectorMapper, never()).updateById(any(DocumentChunkVector.class));
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

    private DocumentChunkVector vector(Long chunkId, Long documentId, String providerName) {
        DocumentChunkVector vector = new DocumentChunkVector();
        vector.setId(chunkId + Math.abs(providerName.hashCode()));
        vector.setUserId(1L);
        vector.setDocumentId(documentId);
        vector.setChunkId(chunkId);
        vector.setProviderName(providerName);
        vector.setVectorJson("{}");
        vector.setStatus(1);
        return vector;
    }

    private EmbeddingClientRouter localRouter() {
        AiProperties aiProperties = new AiProperties();
        return new EmbeddingClientRouter(
                aiProperties,
                List.of(new LocalEmbeddingClient(), new RemoteDenseEmbeddingClient(aiProperties))
        );
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<PgVectorStore> emptyPgVectorStoreProvider() {
        return mock(ObjectProvider.class);
    }

}
