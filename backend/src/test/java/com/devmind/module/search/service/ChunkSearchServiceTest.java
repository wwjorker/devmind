package com.devmind.module.search.service;

import com.devmind.module.document.entity.DocumentChunk;
import com.devmind.module.document.entity.KnowledgeDocument;
import com.devmind.module.document.mapper.DocumentChunkMapper;
import com.devmind.module.document.mapper.KnowledgeDocumentMapper;
import com.devmind.module.search.vo.ChunkSearchResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChunkSearchServiceTest {

    @Test
    void findChunksByIdsShouldRestoreActiveChunksInRequestedOrder() {
        DocumentChunkMapper chunkMapper = mock(DocumentChunkMapper.class);
        KnowledgeDocumentMapper documentMapper = mock(KnowledgeDocumentMapper.class);
        ChunkSearchService searchService = new ChunkSearchService(chunkMapper, documentMapper);

        DocumentChunk chunkOne = chunk(10L, 100L, 0, "first restored chunk");
        DocumentChunk chunkTwo = chunk(11L, 100L, 1, "second restored chunk");
        KnowledgeDocument document = document(100L, "Redis cache note");

        when(chunkMapper.selectList(any())).thenReturn(List.of(chunkTwo, chunkOne));
        when(documentMapper.selectList(any())).thenReturn(List.of(document));

        List<ChunkSearchResponse> responses = searchService.findChunksByIds(1L, List.of(10L, 11L));

        assertThat(responses).hasSize(2);
        assertThat(responses).extracting(ChunkSearchResponse::getChunkId).containsExactly(10L, 11L);
        assertThat(responses).extracting(ChunkSearchResponse::getDocumentTitle)
                .containsExactly("Redis cache note", "Redis cache note");
        assertThat(responses).extracting(ChunkSearchResponse::getContent)
                .containsExactly("first restored chunk", "second restored chunk");
    }

    private DocumentChunk chunk(Long id, Long documentId, Integer chunkIndex, String content) {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setId(id);
        chunk.setDocumentId(documentId);
        chunk.setUserId(1L);
        chunk.setChunkIndex(chunkIndex);
        chunk.setContent(content);
        chunk.setTokenCount(32);
        chunk.setStatus(1);
        return chunk;
    }

    private KnowledgeDocument document(Long id, String title) {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(id);
        document.setUserId(1L);
        document.setTitle(title);
        document.setSourceType("bug_review");
        document.setTags("redis,cache");
        document.setStatus(1);
        return document;
    }
}
