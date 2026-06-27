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

    @Test
    void findChunksByIdsShouldRecalculateScoresWithRetrievalKeywords() {
        DocumentChunkMapper chunkMapper = mock(DocumentChunkMapper.class);
        KnowledgeDocumentMapper documentMapper = mock(KnowledgeDocumentMapper.class);
        ChunkSearchService searchService = new ChunkSearchService(chunkMapper, documentMapper);

        DocumentChunk chunk = chunk(
                10L,
                100L,
                0,
                "Redis cache penetration happens when cache misses repeatedly hit MySQL."
        );
        KnowledgeDocument document = document(100L, "Redis cache penetration review");

        when(chunkMapper.selectList(any())).thenReturn(List.of(chunk));
        when(documentMapper.selectList(any())).thenReturn(List.of(document));

        List<ChunkSearchResponse> responses = searchService.findChunksByIds(
                1L,
                List.of(10L),
                List.of("Redis", "cache", "penetration")
        );

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getScore()).isGreaterThan(1);
    }

    @Test
    void searchChunksShouldIncludeChunksWhenDocumentMetadataMatchesKeyword() {
        DocumentChunkMapper chunkMapper = mock(DocumentChunkMapper.class);
        KnowledgeDocumentMapper documentMapper = mock(KnowledgeDocumentMapper.class);
        ChunkSearchService searchService = new ChunkSearchService(chunkMapper, documentMapper);

        DocumentChunk chunk = chunk(
                10L,
                100L,
                0,
                "This note explains empty-value caching and rate limiting."
        );
        KnowledgeDocument document = document(100L, "Redis cache penetration interview note");

        when(documentMapper.selectList(any())).thenReturn(List.of(document), List.of(document));
        when(chunkMapper.selectList(any())).thenReturn(List.of(chunk));

        List<ChunkSearchResponse> responses = searchService.searchChunks(1L, List.of("Redis"), 3);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getDocumentTitle()).isEqualTo("Redis cache penetration interview note");
        assertThat(responses.get(0).getScore()).isGreaterThan(1);
    }

    @Test
    void searchChunksShouldDownrankDuplicateContentAfterFirstResult() {
        DocumentChunkMapper chunkMapper = mock(DocumentChunkMapper.class);
        KnowledgeDocumentMapper documentMapper = mock(KnowledgeDocumentMapper.class);
        ChunkSearchService searchService = new ChunkSearchService(chunkMapper, documentMapper);

        DocumentChunk firstDuplicate = chunk(
                10L,
                100L,
                0,
                "Redis cache penetration happens when repeated misses hit MySQL."
        );
        DocumentChunk secondDuplicate = chunk(
                11L,
                101L,
                0,
                "Redis cache penetration happens when repeated misses hit MySQL."
        );
        KnowledgeDocument firstDocument = document(100L, "Redis cache penetration review");
        KnowledgeDocument secondDocument = document(101L, "Redis cache penetration copied note");

        when(documentMapper.selectList(any()))
                .thenReturn(List.of(firstDocument, secondDocument), List.of(firstDocument, secondDocument));
        when(chunkMapper.selectList(any())).thenReturn(List.of(firstDuplicate, secondDuplicate));

        List<ChunkSearchResponse> responses = searchService.searchChunks(1L, List.of("Redis", "cache", "penetration"), 5);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getScore()).isGreaterThan(responses.get(1).getScore());
        assertThat(responses).extracting(ChunkSearchResponse::getChunkId).containsExactly(10L, 11L);
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
