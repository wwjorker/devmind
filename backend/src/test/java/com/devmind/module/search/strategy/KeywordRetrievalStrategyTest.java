package com.devmind.module.search.strategy;

import com.devmind.module.search.service.ChunkSearchService;
import com.devmind.module.search.vo.ChunkSearchResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KeywordRetrievalStrategyTest {

    @Test
    void shouldExposeStableStrategyMetadataAndDelegateToChunkSearchService() {
        ChunkSearchService chunkSearchService = mock(ChunkSearchService.class);
        KeywordRetrievalStrategy strategy = new KeywordRetrievalStrategy(chunkSearchService);
        List<String> keywords = List.of("Redis", "cache");
        List<ChunkSearchResponse> expected = List.of(new ChunkSearchResponse(
                1L,
                2L,
                "Redis cache penetration review",
                "bug_review",
                "Redis,cache",
                0,
                "Cache empty values to reduce repeated database misses.",
                42,
                91
        ));
        when(chunkSearchService.searchChunks(7L, keywords, 3)).thenReturn(expected);

        List<ChunkSearchResponse> actual = strategy.retrieve(7L, keywords, 3);

        assertThat(strategy.strategyName()).isEqualTo("mysql-fulltext-keyword-v1");
        assertThat(strategy.description()).contains("FULLTEXT");
        assertThat(actual).isSameAs(expected);
        verify(chunkSearchService).searchChunks(7L, keywords, 3);
    }
}
