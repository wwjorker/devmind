package com.devmind.module.ai.service;

import com.devmind.module.ai.entity.AiAskLog;
import com.devmind.module.ai.mapper.AiAskLogMapper;
import com.devmind.module.ai.vo.RagEvaluationDatasetResponse;
import com.devmind.module.ai.vo.RagRetrievalEvaluationResponse;
import com.devmind.module.search.strategy.KeywordRetrievalStrategy;
import com.devmind.module.search.strategy.RetrievalStrategy;
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
        RetrievalStrategy retrievalStrategy = mock(RetrievalStrategy.class);
        KeywordRetrievalStrategy keywordRetrievalStrategy = mock(KeywordRetrievalStrategy.class);
        RetrievalKeywordService retrievalKeywordService = mock(RetrievalKeywordService.class);

        when(retrievalKeywordService.resolveKeywords(any())).thenAnswer(invocation -> keywordsForQuestion(invocation.getArgument(0)));
        when(retrievalStrategy.strategyName()).thenReturn("hybrid-keyword-local-sparse-vector-rrf-v1");
        when(retrievalStrategy.description()).thenReturn("Keyword plus local sparse-vector rerank fused by RRF");
        when(retrievalStrategy.retrieve(eq(1L), org.mockito.ArgumentMatchers.<List<String>>any(), eq(5)))
                .thenAnswer(invocation -> chunksForKeywords(invocation.getArgument(1)));
        when(keywordRetrievalStrategy.strategyName()).thenReturn("mysql-fulltext-keyword-v1");
        when(keywordRetrievalStrategy.description()).thenReturn("MySQL FULLTEXT plus multi-keyword metadata scoring");
        when(keywordRetrievalStrategy.retrieve(eq(1L), org.mockito.ArgumentMatchers.<List<String>>any(), eq(5)))
                .thenAnswer(invocation -> chunksForKeywords(invocation.getArgument(1)));

        RagEvaluationDatasetService service = new RagEvaluationDatasetService(
                askLogMapper,
                retrievalStrategy,
                keywordRetrievalStrategy,
                retrievalKeywordService
        );

        RagRetrievalEvaluationResponse response = service.retrievalEvaluation(1L);

        assertThat(response.getTotalCaseCount()).isEqualTo(8);
        assertThat(response.getPassedCaseCount()).isEqualTo(8);
        assertThat(response.getPassRate()).isEqualTo(1.0);
        assertThat(response.getPositiveCaseCount()).isEqualTo(7);
        assertThat(response.getEvaluationK()).isEqualTo(3);
        assertThat(response.getRetrievalLimit()).isEqualTo(5);
        assertThat(response.getRetrievalStrategy()).isEqualTo("hybrid-keyword-local-sparse-vector-rrf-v1");
        assertThat(response.getRetrievalStrategyDescription()).contains("RRF");
        assertThat(response.getBaselineRetrievalStrategy()).isEqualTo("mysql-fulltext-keyword-v1");
        assertThat(response.getBaselineRetrievalStrategyDescription()).contains("FULLTEXT");
        assertThat(response.getRelevanceMode()).isEqualTo("gold-document-title");
        assertThat(response.getHitAtK()).isEqualTo(1.0);
        assertThat(response.getMrr()).isEqualTo(1.0);
        assertThat(response.getBaselinePassedCaseCount()).isEqualTo(8);
        assertThat(response.getBaselinePassRate()).isEqualTo(1.0);
        assertThat(response.getBaselineHitAtK()).isEqualTo(1.0);
        assertThat(response.getBaselineMrr()).isEqualTo(1.0);
        assertThat(response.getHitAtKDelta()).isZero();
        assertThat(response.getMrrDelta()).isZero();
        assertThat(response.getCases())
                .filteredOn(caseResponse -> "redis-cache-penetration-basic".equals(caseResponse.getCaseId()))
                .singleElement()
                .satisfies(caseResponse -> {
                    assertThat(caseResponse.getPassed()).isTrue();
                    assertThat(caseResponse.getFirstRelevantRank()).isEqualTo(1);
                    assertThat(caseResponse.getHitAtK()).isTrue();
                    assertThat(caseResponse.getReciprocalRank()).isEqualTo(1.0);
                    assertThat(caseResponse.getRetrievedChunkCount()).isEqualTo(1);
                    assertThat(caseResponse.getTopChunkIds()).containsExactly(17L);
                    assertThat(caseResponse.getRelevantDocumentTitles()).contains("Redis 缓存穿透复盘");
                    assertThat(caseResponse.getMatchedExpectedKeywords()).contains("Redis", "cache", "penetration");
                });
        assertThat(response.getCases())
                .filteredOn(caseResponse -> "unknown-kubernetes-fallback".equals(caseResponse.getCaseId()))
                .singleElement()
                .satisfies(caseResponse -> {
                    assertThat(caseResponse.getPassed()).isTrue();
                    assertThat(caseResponse.getExpectedNoContext()).isTrue();
                    assertThat(caseResponse.getRetrievedChunkCount()).isZero();
                });
    }

    @Test
    void retrievalEvaluationShouldFailPositiveCaseWhenRelevantChunkIsOutsideTopK() {
        AiAskLogMapper askLogMapper = mock(AiAskLogMapper.class);
        RetrievalStrategy retrievalStrategy = mock(RetrievalStrategy.class);
        KeywordRetrievalStrategy keywordRetrievalStrategy = mock(KeywordRetrievalStrategy.class);
        RetrievalKeywordService retrievalKeywordService = mock(RetrievalKeywordService.class);

        when(retrievalKeywordService.resolveKeywords(any())).thenReturn(List.of("Redis"));
        when(retrievalStrategy.strategyName()).thenReturn("mysql-fulltext-keyword-v1");
        when(retrievalStrategy.description()).thenReturn("MySQL FULLTEXT plus multi-keyword metadata scoring");
        when(retrievalStrategy.retrieve(eq(1L), org.mockito.ArgumentMatchers.<List<String>>any(), eq(5))).thenReturn(List.of(
                unrelatedChunk(11L, "unrelated first"),
                unrelatedChunk(12L, "unrelated second"),
                unrelatedChunk(13L, "unrelated third"),
                new ChunkSearchResponse(
                        14L,
                        4L,
                        "Redis cache penetration review",
                        "bug_review",
                        "Redis,cache,penetration",
                        0,
                        "Redis cache penetration can use empty-value caching and rate limiting.",
                        80,
                        50
                )
        ));
        when(keywordRetrievalStrategy.strategyName()).thenReturn("mysql-fulltext-keyword-v1");
        when(keywordRetrievalStrategy.description()).thenReturn("MySQL FULLTEXT plus multi-keyword metadata scoring");
        when(keywordRetrievalStrategy.retrieve(eq(1L), org.mockito.ArgumentMatchers.<List<String>>any(), eq(5)))
                .thenReturn(List.of());

        RagEvaluationDatasetService service = new RagEvaluationDatasetService(
                askLogMapper,
                retrievalStrategy,
                keywordRetrievalStrategy,
                retrievalKeywordService
        );

        RagRetrievalEvaluationResponse response = service.retrievalEvaluation(1L);

        assertThat(response.getCases())
                .filteredOn(caseResponse -> "redis-cache-penetration-basic".equals(caseResponse.getCaseId()))
                .singleElement()
                .satisfies(caseResponse -> {
                    assertThat(caseResponse.getPassed()).isFalse();
                    assertThat(caseResponse.getFirstRelevantRank()).isEqualTo(4);
                    assertThat(caseResponse.getHitAtK()).isFalse();
                    assertThat(caseResponse.getReciprocalRank()).isEqualTo(0.25);
                    assertThat(caseResponse.getNote()).contains("rank #4", "Top 3");
                });
    }

    @Test
    void retrievalEvaluationShouldCompareHybridStrategyWithKeywordBaseline() {
        AiAskLogMapper askLogMapper = mock(AiAskLogMapper.class);
        RetrievalStrategy retrievalStrategy = mock(RetrievalStrategy.class);
        KeywordRetrievalStrategy keywordRetrievalStrategy = mock(KeywordRetrievalStrategy.class);
        RetrievalKeywordService retrievalKeywordService = mock(RetrievalKeywordService.class);

        when(retrievalKeywordService.resolveKeywords(any())).thenAnswer(invocation -> keywordsForQuestion(invocation.getArgument(0)));
        when(retrievalStrategy.strategyName()).thenReturn("hybrid-keyword-local-sparse-vector-rrf-v1");
        when(retrievalStrategy.description()).thenReturn("Keyword plus local sparse-vector rerank fused by RRF");
        when(retrievalStrategy.retrieve(eq(1L), org.mockito.ArgumentMatchers.<List<String>>any(), eq(5)))
                .thenAnswer(invocation -> chunksForKeywords(invocation.getArgument(1)));
        when(keywordRetrievalStrategy.strategyName()).thenReturn("mysql-fulltext-keyword-v1");
        when(keywordRetrievalStrategy.description()).thenReturn("MySQL FULLTEXT plus multi-keyword metadata scoring");
        when(keywordRetrievalStrategy.retrieve(eq(1L), org.mockito.ArgumentMatchers.<List<String>>any(), eq(5)))
                .thenAnswer(invocation -> baselineChunksForKeywords(invocation.getArgument(1)));

        RagEvaluationDatasetService service = new RagEvaluationDatasetService(
                askLogMapper,
                retrievalStrategy,
                keywordRetrievalStrategy,
                retrievalKeywordService
        );

        RagRetrievalEvaluationResponse response = service.retrievalEvaluation(1L);

        assertThat(response.getHitAtK()).isEqualTo(1.0);
        assertThat(response.getMrr()).isEqualTo(1.0);
        assertThat(response.getBaselineHitAtK()).isZero();
        assertThat(response.getBaselineMrr()).isEqualTo(0.0714);
        assertThat(response.getHitAtKDelta()).isEqualTo(1.0);
        assertThat(response.getMrrDelta()).isEqualTo(0.9286);
    }

    private List<String> keywordsForQuestion(String question) {
        if (question.contains("Kubernetes")) {
            return List.of("Kubernetes");
        }
        if (question.contains("JWT")) {
            return List.of("JWT", "Redis", "黑名单");
        }
        if (question.contains("Flyway")) {
            return List.of("Flyway", "migration");
        }
        if (question.contains("LlmClient")) {
            return List.of("LlmClient", "DeepSeek");
        }
        if (question.contains("检索不到")) {
            return List.of("fallback", "chunks");
        }
        if (question.contains("RAG")) {
            return List.of("RAG", "evaluation");
        }
        return List.of("Redis", "缓存穿透");
    }

    private List<ChunkSearchResponse> chunksForKeywords(List<String> keywords) {
        if (keywords.contains("Kubernetes")) {
            return List.of();
        }
        if (keywords.contains("JWT")) {
            return List.of(chunk(18L, "JWT 退出登录与 Redis 黑名单", "JWT logout uses a Redis blacklist."));
        }
        if (keywords.contains("Flyway")) {
            return List.of(chunk(19L, "Flyway migration 数据库迁移", "Flyway manages database migration scripts."));
        }
        if (keywords.contains("LlmClient")) {
            return List.of(chunk(20L, "LlmClient 与 LLM Provider 抽象", "LlmClient separates DeepSeek and Mock providers."));
        }
        if (keywords.contains("fallback")) {
            return List.of(chunk(21L, "RAG 无上下文兜底", "No-context fallback avoids hallucination when retrieval chunks are empty."));
        }
        if (keywords.contains("RAG")) {
            return List.of(chunk(22L, "RAG 回答质量评估", "RAG evaluation uses bad cases, Hit@K, MRR, and hit rate metrics."));
        }
        return List.of(chunk(17L, "Redis 缓存穿透复盘", "Redis cache penetration can use empty-value caching and rate limiting."));
    }

    private List<ChunkSearchResponse> baselineChunksForKeywords(List<String> keywords) {
        if (keywords.contains("Kubernetes")) {
            return List.of();
        }
        return List.of(
                unrelatedChunk(11L, "unrelated first"),
                unrelatedChunk(12L, "unrelated second"),
                unrelatedChunk(13L, "unrelated third"),
                chunk(14L, "Redis cache penetration review", "Redis cache penetration can use empty-value caching and rate limiting.")
        );
    }

    private ChunkSearchResponse chunk(Long chunkId, String title, String content) {
        return new ChunkSearchResponse(
                chunkId,
                chunkId,
                title,
                "demo_note",
                title,
                0,
                content,
                80,
                91
        );
    }

    private ChunkSearchResponse unrelatedChunk(Long chunkId, String title) {
        return new ChunkSearchResponse(
                chunkId,
                10L,
                title,
                "general_note",
                "general",
                0,
                "This note is unrelated to the expected evidence.",
                40,
                10
        );
    }

    private RagEvaluationDatasetService newService(AiAskLogMapper askLogMapper) {
        return new RagEvaluationDatasetService(
                askLogMapper,
                mock(RetrievalStrategy.class),
                mock(KeywordRetrievalStrategy.class),
                mock(RetrievalKeywordService.class)
        );
    }
}
