package com.devmind.module.document.service;

import com.devmind.common.exception.BizException;
import com.devmind.module.document.entity.KnowledgeDocument;
import com.devmind.module.document.mapper.KnowledgeDocumentMapper;
import com.devmind.module.document.vo.DocumentResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class KnowledgeDocumentServiceTest {

    @Test
    void importFromFileShouldCreateDocumentAndRebuildChunks() {
        KnowledgeDocumentMapper documentMapper = mock(KnowledgeDocumentMapper.class);
        DocumentChunkService chunkService = mock(DocumentChunkService.class);
        doAnswer(invocation -> {
            KnowledgeDocument document = invocation.getArgument(0);
            document.setId(42L);
            return 1;
        }).when(documentMapper).insert(any(KnowledgeDocument.class));
        KnowledgeDocumentService documentService = new KnowledgeDocumentService(documentMapper, chunkService);
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
        assertThat(response.getSourceType()).isEqualTo("imported_note");
        assertThat(response.getTags()).isEqualTo("redis,cache");
        assertThat(response.getContent()).contains("Redis cache penetration");
        verify(chunkService).rebuildChunks(eq(1L), eq(42L), eq(response.getContent()));
    }

    @Test
    void importFromFileShouldRejectUnsupportedFileType() {
        KnowledgeDocumentService documentService = new KnowledgeDocumentService(
                mock(KnowledgeDocumentMapper.class),
                mock(DocumentChunkService.class)
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
}
