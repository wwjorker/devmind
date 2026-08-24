package com.devmind.module.document.service;

import com.devmind.common.exception.BizException;
import com.devmind.module.document.dto.UpdateDocumentRequest;
import com.devmind.module.document.entity.DocumentChunk;
import com.devmind.module.document.entity.KnowledgeDocument;
import com.devmind.module.document.mapper.KnowledgeDocumentMapper;
import com.devmind.module.document.vo.DocumentResponse;
import com.devmind.module.search.service.ChunkVectorService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeDocumentServiceTest {

    @Test
    void importFromFileShouldCreateDocumentAndRebuildChunks() {
        KnowledgeDocumentMapper documentMapper = mock(KnowledgeDocumentMapper.class);
        KnowledgeDocumentVersionService versionService = mock(KnowledgeDocumentVersionService.class);
        DocumentChunkService chunkService = mock(DocumentChunkService.class);
        ChunkVectorService chunkVectorService = mock(ChunkVectorService.class);
        TrackingTransactionOperations transactions = new TrackingTransactionOperations();
        doAnswer(invocation -> {
            assertThat(transactions.isActive()).isTrue();
            KnowledgeDocument document = invocation.getArgument(0);
            document.setId(42L);
            return 1;
        }).when(documentMapper).insert(any(KnowledgeDocument.class));
        DocumentChunk chunk = chunk(10L, 42L, "Redis cache penetration");
        when(chunkService.replaceChunks(any(), any(), any())).thenAnswer(invocation -> {
            assertThat(transactions.isActive()).isTrue();
            return List.of(chunk);
        });
        doAnswer(invocation -> {
            assertThat(transactions.isActive()).isTrue();
            return null;
        }).when(chunkVectorService).archiveMySqlByDocument(any(), any());
        doAnswer(invocation -> {
            assertThat(transactions.isActive()).isFalse();
            return null;
        }).when(chunkVectorService).rebuildVectors(any(), any(), any());
        KnowledgeDocumentService documentService = new KnowledgeDocumentService(
                documentMapper, versionService, chunkService, chunkVectorService, transactions);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "redis-note.md",
                "text/markdown",
                "# Redis cache penetration\nCache empty values for missing keys.".getBytes(StandardCharsets.UTF_8)
        );

        DocumentResponse response = documentService.importFromFile(
                1L,
                file,
                "",
                "",
                "redis,cache",
                ""
        );

        assertThat(response.getId()).isEqualTo(42L);
        assertThat(response.getTitle()).isEqualTo("redis-note");
        assertThat(response.getSourceType()).isEqualTo("learning_note");
        assertThat(response.getTags()).isEqualTo("redis,cache");
        assertThat(response.getContent()).contains("Redis cache penetration");
        verify(chunkService).replaceChunks(eq(1L), eq(42L), eq(response.getContent()));
        verify(versionService).snapshot(
                any(KnowledgeDocument.class),
                eq(DocumentVersionOrigin.FILE_IMPORT),
                eq(null),
                eq(null));
        verify(chunkVectorService).archiveMySqlByDocument(1L, 42L);
        verify(chunkVectorService).archiveServingIndexByDocument(1L, 42L);
        verify(chunkVectorService).rebuildVectors(1L, 42L, List.of(chunk));
    }

    @Test
    void importFromFileShouldRejectUnsupportedFileType() {
        KnowledgeDocumentService documentService = new KnowledgeDocumentService(
                mock(KnowledgeDocumentMapper.class),
                mock(KnowledgeDocumentVersionService.class),
                mock(DocumentChunkService.class),
                mock(ChunkVectorService.class),
                new TrackingTransactionOperations()
        );
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "note.pdf",
                "application/pdf",
                "not supported".getBytes(StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> documentService.importFromFile(1L, file, null, null, null, null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("only .txt, .md, and .markdown files are supported");
    }

    @Test
    void archiveShouldArchiveOwnedDocumentAndItsChunks() {
        KnowledgeDocumentMapper documentMapper = mock(KnowledgeDocumentMapper.class);
        KnowledgeDocumentVersionService versionService = mock(KnowledgeDocumentVersionService.class);
        DocumentChunkService chunkService = mock(DocumentChunkService.class);
        ChunkVectorService chunkVectorService = mock(ChunkVectorService.class);
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(42L);
        document.setUserId(7L);
        document.setStatus(1);
        document.setVersionNo(1);
        when(documentMapper.selectOne(any())).thenReturn(document);
        when(documentMapper.updateById(document)).thenAnswer(invocation -> {
            document.setVersionNo(document.getVersionNo() + 1);
            return 1;
        });
        KnowledgeDocumentService documentService = new KnowledgeDocumentService(
                documentMapper, versionService, chunkService, chunkVectorService,
                new TrackingTransactionOperations());

        documentService.archive(7L, 42L);

        assertThat(document.getStatus()).isZero();
        assertThat(document.getVersionNo()).isEqualTo(2);
        verify(documentMapper).updateById(document);
        verify(versionService).snapshot(
                document, DocumentVersionOrigin.USER_ARCHIVE, null, null);
        verify(chunkService).archiveByDocument(7L, 42L);
        verify(chunkVectorService).archiveMySqlByDocument(7L, 42L);
        verify(chunkVectorService).archiveServingIndexByDocument(7L, 42L);
    }

    @Test
    void restoreShouldReactivateOwnedDocumentAndRebuildChunks() {
        KnowledgeDocumentMapper documentMapper = mock(KnowledgeDocumentMapper.class);
        KnowledgeDocumentVersionService versionService = mock(KnowledgeDocumentVersionService.class);
        DocumentChunkService chunkService = mock(DocumentChunkService.class);
        ChunkVectorService chunkVectorService = mock(ChunkVectorService.class);
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(42L);
        document.setUserId(7L);
        document.setContent("restored content");
        document.setStatus(0);
        document.setVersionNo(1);
        when(documentMapper.selectOne(any())).thenReturn(document);
        when(documentMapper.updateById(document)).thenAnswer(invocation -> {
            document.setVersionNo(document.getVersionNo() + 1);
            return 1;
        });
        DocumentChunk chunk = chunk(10L, 42L, "restored content");
        when(chunkService.replaceChunks(7L, 42L, "restored content")).thenReturn(List.of(chunk));
        KnowledgeDocumentService documentService = new KnowledgeDocumentService(
                documentMapper, versionService, chunkService, chunkVectorService,
                new TrackingTransactionOperations());

        DocumentResponse response = documentService.restore(7L, 42L);

        assertThat(response.getStatus()).isEqualTo(1);
        assertThat(document.getVersionNo()).isEqualTo(2);
        verify(documentMapper).updateById(document);
        verify(versionService).snapshot(
                document, DocumentVersionOrigin.USER_RESTORE, null, null);
        verify(chunkService).replaceChunks(7L, 42L, "restored content");
        verify(chunkVectorService).rebuildVectors(7L, 42L, List.of(chunk));
    }

    @Test
    void updateShouldKeepCommittedContentWhenVectorIndexingFails() {
        KnowledgeDocumentMapper documentMapper = mock(KnowledgeDocumentMapper.class);
        KnowledgeDocumentVersionService versionService = mock(KnowledgeDocumentVersionService.class);
        DocumentChunkService chunkService = mock(DocumentChunkService.class);
        ChunkVectorService chunkVectorService = mock(ChunkVectorService.class);
        TrackingTransactionOperations transactions = new TrackingTransactionOperations();
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(42L);
        document.setUserId(7L);
        document.setStatus(1);
        document.setVersionNo(1);
        when(documentMapper.selectOne(any())).thenReturn(document);
        when(documentMapper.updateById(document)).thenAnswer(invocation -> {
            document.setVersionNo(document.getVersionNo() + 1);
            return 1;
        });
        DocumentChunk chunk = chunk(11L, 42L, "new searchable content");
        when(chunkService.replaceChunks(7L, 42L, "new searchable content")).thenReturn(List.of(chunk));
        doAnswer(invocation -> {
            assertThat(transactions.isActive()).isFalse();
            throw new IllegalStateException("embedding unavailable");
        }).when(chunkVectorService).rebuildVectors(7L, 42L, List.of(chunk));
        KnowledgeDocumentService documentService = new KnowledgeDocumentService(
                documentMapper, versionService, chunkService, chunkVectorService, transactions);
        UpdateDocumentRequest request = new UpdateDocumentRequest();
        request.setTitle("updated");
        request.setContent("new searchable content");
        request.setSourceType("bug_review");
        request.setTags("redis");
        request.setSummary("updated summary");

        DocumentResponse response = documentService.update(7L, 42L, request);

        assertThat(response.getContent()).isEqualTo("new searchable content");
        assertThat(response.getStatus()).isEqualTo(1);
        assertThat(document.getVersionNo()).isEqualTo(2);
        verify(documentMapper).updateById(document);
        verify(versionService).snapshot(
                document, DocumentVersionOrigin.USER_UPDATE, null, null);
        verify(chunkService).replaceChunks(7L, 42L, "new searchable content");
        verify(chunkVectorService).archiveMySqlByDocument(7L, 42L);
        verify(chunkVectorService).archiveServingIndexByDocument(7L, 42L);
    }

    private DocumentChunk chunk(Long id, Long documentId, String content) {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setId(id);
        chunk.setDocumentId(documentId);
        chunk.setUserId(7L);
        chunk.setContent(content);
        chunk.setStatus(1);
        return chunk;
    }

    private static final class TrackingTransactionOperations implements TransactionOperations {

        private final AtomicBoolean active = new AtomicBoolean();

        @Override
        public <T> T execute(TransactionCallback<T> action) {
            assertThat(active.compareAndSet(false, true)).isTrue();
            TransactionStatus status = new SimpleTransactionStatus();
            try {
                return action.doInTransaction(status);
            } finally {
                active.set(false);
            }
        }

        private boolean isActive() {
            return active.get();
        }
    }
}
