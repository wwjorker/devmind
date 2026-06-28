package com.devmind.module.ai.service;

import com.devmind.module.ai.entity.AiAskLog;
import com.devmind.module.ai.mapper.AiAskLogMapper;
import com.devmind.module.ai.vo.RagEvaluationDatasetResponse;
import com.devmind.module.ai.vo.RagRetrievalEvaluationResponse;
import com.devmind.module.search.service.ChunkSearchService;
import com.devmind.module.search.vo.ChunkSearchResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RagEvaluationDatasetServiceTest {

    @Test
    void datasetShouldReturnStaticCasesWhenNoAskLogsExist() {
        AiAskLogMapper askLogMapper = mock(AiAskLogMapper.class);
        when(askLogMapper.selectList(any())).thenReturn(List.of());
        RagEvaluationDatasetService service = newService(askLogMapper);

        RagEvaluationDatasetResponse response = service.dataset(1L);

        assertThat(response.getTotalCaseCount()).isEqualTo(8);
        assertThat(response.getCoveredCaseCount()).isZero();
        assertThat(response.getCoverageRate()).isZero();
        assertThat(response.getCases())
                .extracting("caseId")
                .contains("redis-cache-penetration-basic", "unknown-kubernetes-fallback");
    }

    @Test
    void datasetShouldMarkCaseCoveredByLatestMatchingAskLog() {
        AiAskLogMapper askLogMapper = mock(AiAskLogMapper.class);
        AiAskLog log = new AiAskLog();
        log.setId(42L);
        log.setQuestion("面试中应该如何解释 Redis 缓存穿透？");
        log.setStatus(1);
        log.setRetrievedChunkCount(3);
        log.setRetrievedChunkIds("3,4,7");
        log.setCreatedAt(LocalDateTime.of(2026, 6, 25, 10, 0));
        when(askLogMapper.selectList(any())).thenReturn(List.of(log));
        RagEvaluationDatasetService service = newService(askLogMapper);

        RagEvaluationDatasetResponse response = service.dataset(1L);

        assertThat(response.getCoveredCaseCount()).isEqualTo(1);
        assertThat(response.getCoverageRate()).isEqualTo(0.125);
        assertThat(response.getCases())
                .filteredOn(caseResponse -> "redis-cache-penetration-basic".equals(caseResponse.getCaseId()))
                .singleElement()
                .satisfies(caseResponse -> {
                    assertThat(caseResponse.getCovered()).isTrue();
                    assertThat(caseResponse.getLastAskLogId()).isEqualTo(42L);
                    assertThat(caseResponse.getLastRetrievedChunkCount()).isEqualTo(3);
                    assertThat(caseResponse.getLastRetrievedChunkIds()).isEqualTo("3,4,7");
                });
    }

    @Test
    void retrievalEvaluationShouldRunRetrievalAgainstStaticCases() {
        AiAskLogMapper askLogMapper = mock(AiAskLogMapper.class);
        ChunkSearchService chunkSearchService = mock(ChunkSearchService.class);
        RetrievalKeywordService retrievalKeywordService = mock(RetrievalKeywordService.class);

        when(retrievalKeywordService.resolveKeywords(any())).thenReturn(List.of("Redis"));
        when(chunkSearchService.searchChunks(eq(1L), org.mockito.ArgumentMatchers.<List<String>>any(), eq(5))).thenReturn(List.of(
                new ChunkSearchResponse(
                        3L,
                        2L,
                        "Redis cache penetration review",
                        "bug_review",
                        "Redis,cache,penetration,JWT,Flyway,LlmClient,RAG,evaluation,bad case",
                        0,
                        "Redis cache penetration can use empty-value caching, rate limiting, JWT logout blacklist, Flyway migration, LlmClient provider abstraction, and RAG evaluation with bad cases.",
                        120,
                        91
                )
        ));

        RagEvaluationDatasetService service = new RagEvaluationDatasetService(
                askLogMapper,
                chunkSearchService,
                retrievalKeywordService
        );

        RagRetrievalEvaluationResponse response = service.retrievalEvaluation(1L);

        assertThat(response.getTotalCaseCount()).isEqualTo(8);
        assertThat(response.getPassedCaseCount()).isEqualTo(7);
        assertThat(response.getPassRate()).isEqualTo(0.875);
        assertThat(response.getCases())
                .filteredOn(caseResponse -> "redis-cache-penetration-basic".equals(caseResponse.getCaseId()))
                .singleElement()
                .satisfies(caseResponse -> {
                    assertThat(caseResponse.getPassed()).isTrue();
                    assertThat(caseResponse.getRetrievedChunkCount()).isEqualTo(1);
                    assertThat(caseResponse.getTopChunkIds()).containsExactly(3L);
                    assertThat(caseResponse.getMatchedExpectedKeywords()).contains("Redis", "cache", "penetration");
                });
        assertThat(response.getCases())
                .filteredOn(caseResponse -> "unknown-kubernetes-fallback".equals(caseResponse.getCaseId()))
                .singleElement()
                .satisfies(caseResponse -> assertThat(caseResponse.getPassed()).isFalse());
    }

    private RagEvaluationDatasetService newService(AiAskLogMapper askLogMapper) {
        return new RagEvaluationDatasetService(
                askLogMapper,
                mock(ChunkSearchService.class),
                mock(RetrievalKeywordService.class)
        );
    }
}
