package com.devmind.module.ai.service;

import com.devmind.module.ai.entity.AiAskLog;
import com.devmind.module.ai.mapper.AiAskLogMapper;
import com.devmind.module.ai.vo.RagEvaluationDatasetResponse;
import com.devmind.module.ai.vo.RagRetrievalEvaluationResponse;
import com.devmind.module.ai.vo.RagRetrievalStrategyEvaluationResponse;
import com.devmind.common.api.ResultCode;
import com.devmind.common.exception.BizException;
import com.devmind.module.ai.config.AiProperties;
import com.devmind.module.search.rerank.NoneRerankClient;
import com.devmind.module.search.rerank.RemoteRerankClient;
import com.devmind.module.search.rerank.RerankClientRouter;
import com.devmind.module.search.strategy.HybridRetrievalStrategy;
import com.devmind.module.search.strategy.KeywordRetrievalStrategy;
import com.devmind.module.search.vo.ChunkSearchResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagEvaluationDatasetServiceTest {

    @Test
    void datasetShouldReturnStaticCasesWhenNoAskLogsExist() {
        AiAskLogMapper askLogMapper = mock(AiAskLogMapper.class);
        when(askLogMapper.selectList(any())).thenReturn(List.of());
        RagEvaluationDatasetService service = newService(askLogMapper);

        RagEvaluationDatasetResponse response = service.dataset(1L);

        assertThat(response.getTotalCaseCount()).isEqualTo(40);
        assertThat(response.getCoveredCaseCount()).isZero();
        assertThat(response.getCoverageRate()).isZero();
        assertThat(response.getCases())
                .extracting("caseId")
                .contains(
                        "redis-cache-penetration-basic",
                        "redis-missing-key-db-pressure",
                        "token-still-valid-after-logout",
                        "unknown-kubernetes-fallback"
                );
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
        assertThat(response.getCoverageRate()).isEqualTo(0.025);
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
        HybridRetrievalStrategy retrievalStrategy = mock(HybridRetrievalStrategy.class);
        KeywordRetrievalStrategy keywordRetrievalStrategy = mock(KeywordRetrievalStrategy.class);
        RetrievalKeywordService retrievalKeywordService = mock(RetrievalKeywordService.class);

        when(retrievalKeywordService.resolveKeywords(any())).thenAnswer(invocation -> keywordsForQuestion(invocation.getArgument(0)));
        when(retrievalStrategy.strategyName()).thenReturn("hybrid-keyword-local-sparse-vector-rrf-v1");
        when(retrievalStrategy.description()).thenReturn("Keyword plus local sparse-vector rerank fused by RRF");
        when(retrievalStrategy.retrieveWithEmbeddingProvider(eq(1L), org.mockito.ArgumentMatchers.<List<String>>any(), eq(5), eq("local-sparse-vector")))
                .thenAnswer(invocation -> chunksForKeywords(invocation.getArgument(1)));
        when(retrievalStrategy.retrieveWithEmbeddingProvider(eq(1L), org.mockito.ArgumentMatchers.<List<String>>any(), eq(5), eq("remote-dense")))
                .thenAnswer(invocation -> chunksForKeywords(invocation.getArgument(1)));
        when(retrievalStrategy.retrieveWithEmbeddingProviderAndPgStore(eq(1L), org.mockito.ArgumentMatchers.<List<String>>any(), eq(5), eq("remote-dense")))
                .thenThrow(new IllegalStateException("pgvector store is not enabled (devmind.vector-store.provider)"));
        when(keywordRetrievalStrategy.strategyName()).thenReturn("mysql-fulltext-keyword-v1");
        when(keywordRetrievalStrategy.description()).thenReturn("MySQL FULLTEXT plus multi-keyword metadata scoring");
        when(keywordRetrievalStrategy.retrieve(eq(1L), org.mockito.ArgumentMatchers.<List<String>>any(), eq(5)))
                .thenAnswer(invocation -> chunksForKeywords(invocation.getArgument(1)));

        RagEvaluationDatasetService service = new RagEvaluationDatasetService(
                askLogMapper,
                retrievalStrategy,
                keywordRetrievalStrategy,
                retrievalKeywordService,
                rerankRouter()
        );

        RagRetrievalEvaluationResponse response = service.retrievalEvaluation(1L);

        assertThat(response.getTotalCaseCount()).isEqualTo(40);
        assertThat(response.getPassedCaseCount()).isEqualTo(40);
        assertThat(response.getPassRate()).isEqualTo(1.0);
        assertThat(response.getPositiveCaseCount()).isEqualTo(35);
        assertThat(response.getEvaluationK()).isEqualTo(3);
        assertThat(response.getRetrievalLimit()).isEqualTo(5);
        assertThat(response.getRetrievalStrategy()).isEqualTo("hybrid-keyword-local-sparse-vector-rrf-v1");
        assertThat(response.getRetrievalStrategyDescription()).contains("RRF");
        assertThat(response.getBaselineRetrievalStrategy()).isEqualTo("mysql-fulltext-keyword-v1");
        assertThat(response.getBaselineRetrievalStrategyDescription()).contains("FULLTEXT");
        assertThat(response.getRelevanceMode()).isEqualTo("gold-document-title");
        assertThat(response.getHitAtK()).isEqualTo(1.0);
        assertThat(response.getMrr()).isEqualTo(1.0);
        assertThat(response.getBaselinePassedCaseCount()).isEqualTo(40);
        assertThat(response.getBaselinePassRate()).isEqualTo(1.0);
        assertThat(response.getBaselineHitAtK()).isEqualTo(1.0);
        assertThat(response.getBaselineMrr()).isEqualTo(1.0);
        assertThat(response.getHitAtKDelta()).isZero();
        assertThat(response.getMrrDelta()).isZero();
        assertThat(response.getStrategyResults())
                .extracting(RagRetrievalStrategyEvaluationResponse::getStrategyKey)
                .containsExactly("keyword-baseline", "sparse-hybrid", "dense-hybrid", "dense-hybrid-pgvector", "dense-hybrid-rerank");
        assertThat(response.getStrategyResults())
                .filteredOn(result -> "dense-hybrid".equals(result.getStrategyKey()))
                .singleElement()
                .satisfies(result -> {
                    assertThat(result.getStatus()).isEqualTo("available");
                    assertThat(result.getEmbeddingProvider()).isEqualTo("remote-dense");
                    assertThat(result.getHitAtK()).isEqualTo(1.0);
                    assertThat(result.getMrr()).isEqualTo(1.0);
                });
        assertThat(response.getStrategyResults())
                .filteredOn(result -> "dense-hybrid-pgvector".equals(result.getStrategyKey()))
                .singleElement()
                .satisfies(result -> {
                    assertThat(result.getStatus()).isEqualTo("unavailable");
                    assertThat(result.getUnavailableReason()).contains("pgvector store is not enabled");
                    assertThat(result.getCases()).isEmpty();
                });
        assertThat(response.getStrategyResults())
                .filteredOn(result -> "dense-hybrid-rerank".equals(result.getStrategyKey()))
                .singleElement()
                .satisfies(result -> {
                    assertThat(result.getStatus()).isEqualTo("unavailable");
                    assertThat(result.getUnavailableReason()).contains("rerank provider is not configured");
                    assertThat(result.getCases()).isEmpty();
                });
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
        HybridRetrievalStrategy retrievalStrategy = mock(HybridRetrievalStrategy.class);
        KeywordRetrievalStrategy keywordRetrievalStrategy = mock(KeywordRetrievalStrategy.class);
        RetrievalKeywordService retrievalKeywordService = mock(RetrievalKeywordService.class);

        when(retrievalKeywordService.resolveKeywords(any())).thenReturn(List.of("Redis"));
        when(retrievalStrategy.strategyName()).thenReturn("mysql-fulltext-keyword-v1");
        when(retrievalStrategy.description()).thenReturn("MySQL FULLTEXT plus multi-keyword metadata scoring");
        when(retrievalStrategy.retrieveWithEmbeddingProvider(eq(1L), org.mockito.ArgumentMatchers.<List<String>>any(), eq(5), eq("local-sparse-vector"))).thenReturn(List.of(
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
        when(retrievalStrategy.retrieveWithEmbeddingProvider(eq(1L), org.mockito.ArgumentMatchers.<List<String>>any(), eq(5), eq("remote-dense")))
                .thenReturn(List.of());
        when(keywordRetrievalStrategy.strategyName()).thenReturn("mysql-fulltext-keyword-v1");
        when(keywordRetrievalStrategy.description()).thenReturn("MySQL FULLTEXT plus multi-keyword metadata scoring");
        when(keywordRetrievalStrategy.retrieve(eq(1L), org.mockito.ArgumentMatchers.<List<String>>any(), eq(5)))
                .thenReturn(List.of());

        RagEvaluationDatasetService service = new RagEvaluationDatasetService(
                askLogMapper,
                retrievalStrategy,
                keywordRetrievalStrategy,
                retrievalKeywordService,
                rerankRouter()
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
        HybridRetrievalStrategy retrievalStrategy = mock(HybridRetrievalStrategy.class);
        KeywordRetrievalStrategy keywordRetrievalStrategy = mock(KeywordRetrievalStrategy.class);
        RetrievalKeywordService retrievalKeywordService = mock(RetrievalKeywordService.class);

        when(retrievalKeywordService.resolveKeywords(any())).thenAnswer(invocation -> keywordsForQuestion(invocation.getArgument(0)));
        when(retrievalStrategy.strategyName()).thenReturn("hybrid-keyword-local-sparse-vector-rrf-v1");
        when(retrievalStrategy.description()).thenReturn("Keyword plus local sparse-vector rerank fused by RRF");
        when(retrievalStrategy.retrieveWithEmbeddingProvider(eq(1L), org.mockito.ArgumentMatchers.<List<String>>any(), eq(5), eq("local-sparse-vector")))
                .thenAnswer(invocation -> chunksForKeywords(invocation.getArgument(1)));
        when(retrievalStrategy.retrieveWithEmbeddingProvider(eq(1L), org.mockito.ArgumentMatchers.<List<String>>any(), eq(5), eq("remote-dense")))
                .thenAnswer(invocation -> chunksForKeywords(invocation.getArgument(1)));
        when(keywordRetrievalStrategy.strategyName()).thenReturn("mysql-fulltext-keyword-v1");
        when(keywordRetrievalStrategy.description()).thenReturn("MySQL FULLTEXT plus multi-keyword metadata scoring");
        when(keywordRetrievalStrategy.retrieve(eq(1L), org.mockito.ArgumentMatchers.<List<String>>any(), eq(5)))
                .thenAnswer(invocation -> baselineChunksForKeywords(invocation.getArgument(1)));

        RagEvaluationDatasetService service = new RagEvaluationDatasetService(
                askLogMapper,
                retrievalStrategy,
                keywordRetrievalStrategy,
                retrievalKeywordService,
                rerankRouter()
        );

        RagRetrievalEvaluationResponse response = service.retrievalEvaluation(1L);

        assertThat(response.getHitAtK()).isEqualTo(1.0);
        assertThat(response.getMrr()).isEqualTo(1.0);
        assertThat(response.getBaselineHitAtK()).isZero();
        assertThat(response.getBaselineMrr()).isLessThan(response.getMrr());
        assertThat(response.getHitAtKDelta()).isEqualTo(1.0);
        assertThat(response.getMrrDelta()).isGreaterThan(0.0);
    }

    @Test
    void retrievalEvaluationShouldMarkDenseHybridUnavailableWhenRemoteProviderIsNotConfigured() {
        AiAskLogMapper askLogMapper = mock(AiAskLogMapper.class);
        HybridRetrievalStrategy retrievalStrategy = mock(HybridRetrievalStrategy.class);
        KeywordRetrievalStrategy keywordRetrievalStrategy = mock(KeywordRetrievalStrategy.class);
        RetrievalKeywordService retrievalKeywordService = mock(RetrievalKeywordService.class);

        when(retrievalKeywordService.resolveKeywords(any())).thenAnswer(invocation -> keywordsForQuestion(invocation.getArgument(0)));
        when(retrievalStrategy.strategyName()).thenReturn("hybrid-keyword-local-sparse-vector-rrf-v1");
        when(retrievalStrategy.description()).thenReturn("Keyword plus local sparse-vector rerank fused by RRF");
        when(retrievalStrategy.retrieveWithEmbeddingProvider(eq(1L), org.mockito.ArgumentMatchers.<List<String>>any(), eq(5), eq("local-sparse-vector")))
                .thenAnswer(invocation -> chunksForKeywords(invocation.getArgument(1)));
        when(retrievalStrategy.retrieveWithEmbeddingProvider(eq(1L), org.mockito.ArgumentMatchers.<List<String>>any(), eq(5), eq("remote-dense")))
                .thenThrow(new BizException(ResultCode.INTERNAL_ERROR, "external embedding provider is not configured"));
        when(keywordRetrievalStrategy.strategyName()).thenReturn("mysql-fulltext-keyword-v1");
        when(keywordRetrievalStrategy.description()).thenReturn("MySQL FULLTEXT plus multi-keyword metadata scoring");
        when(keywordRetrievalStrategy.retrieve(eq(1L), org.mockito.ArgumentMatchers.<List<String>>any(), eq(5)))
                .thenAnswer(invocation -> chunksForKeywords(invocation.getArgument(1)));

        RagEvaluationDatasetService service = new RagEvaluationDatasetService(
                askLogMapper,
                retrievalStrategy,
                keywordRetrievalStrategy,
                retrievalKeywordService,
                rerankRouter()
        );

        RagRetrievalEvaluationResponse response = service.retrievalEvaluation(1L);

        assertThat(response.getHitAtK()).isEqualTo(1.0);
        assertThat(response.getBaselineHitAtK()).isEqualTo(1.0);
        assertThat(response.getStrategyResults())
                .filteredOn(result -> "dense-hybrid".equals(result.getStrategyKey()))
                .singleElement()
                .satisfies(result -> {
                    assertThat(result.getStatus()).isEqualTo("unavailable");
                    assertThat(result.getUnavailableReason()).contains("external embedding provider is not configured");
                    assertThat(result.getHitAtK()).isNull();
                    assertThat(result.getMrr()).isNull();
                    assertThat(result.getCases()).isEmpty();
                });
        verify(retrievalStrategy, never()).retrieve(eq(1L), org.mockito.ArgumentMatchers.<List<String>>any(), eq(5));
    }

    private List<String> keywordsForQuestion(String question) {
        if (question.contains("Kubernetes")) {
            return List.of("Kubernetes");
        }
        if (question.contains("Kafka")) {
            return List.of("Kafka");
        }
        if (question.contains("Elasticsearch")) {
            return List.of("Elasticsearch");
        }
        if (question.contains("Docker")) {
            return List.of("Docker");
        }
        if (question.contains("Spring Cloud Gateway")) {
            return List.of("Spring Cloud Gateway");
        }
        if (question.contains("联合索引")
                || question.contains("覆盖索引")
                || question.contains("索引优化")) {
            return List.of("MYSQL_INDEX");
        }
        if (question.contains("可重复读")
                || question.contains("事务隔离")
                || question.contains("并发更新")) {
            return List.of("MYSQL_TRANSACTION");
        }
        if (question.contains("线程池")
                || question.contains("拒绝策略")) {
            return List.of("THREAD_POOL");
        }
        if (question.contains("Bean")
                || question.contains("IoC")
                || question.contains("后置处理器")) {
            return List.of("SPRING_BEAN");
        }
        if (question.contains("慢 SQL")
                || question.contains("explain")
                || question.contains("执行计划")) {
            return List.of("MYSQL_SLOW_QUERY");
        }
        if (question.contains("RDB")
                || question.contains("AOF")
                || question.contains("持久化")) {
            return List.of("REDIS_PERSISTENCE");
        }
        if (question.contains("分布式锁")
                || question.contains("扣库存")
                || question.contains("锁自动过期")) {
            return List.of("REDIS_LOCK");
        }
        if (question.contains("HTTP")
                || question.contains("HTTPS")
                || question.contains("TCP")) {
            return List.of("HTTP_TCP");
        }
        if (question.contains("JWT")
                || question.contains("token")
                || question.contains("令牌")
                || question.contains("黑名单")
                || question.contains("退出后")
                || question.contains("閫€鍑哄悗")) {
            return List.of("JWT", "Redis", "榛戝悕鍗?");
        }
        if (question.contains("Flyway")
                || question.contains("手动改表")
                || question.contains("数据库变更")
                || question.contains("表结构")
                || question.contains("版本漂移")) {
            return List.of("Flyway", "migration");
        }
        if (question.contains("回答质量") || question.contains("离线指标") || question.contains("量化")) {
            return List.of("RAG", "evaluation");
        }
        if (question.contains("LlmClient")
                || question.contains("模型供应商")
                || question.contains("DeepSeek")
                || question.contains("Mock")
                || question.contains("真的请求")) {
            return List.of("LlmClient", "DeepSeek");
        }
        if (question.contains("妫€绱笉鍒?")
                || question.contains("知识库里没资料")
                || question.contains("硬编答案")
                || (question.contains("chunk") && question.contains("DevMind"))) {
            return List.of("fallback", "chunks");
        }
        if (question.contains("RAG")) {
            return List.of("RAG", "evaluation");
        }
        return List.of("Redis", "缂撳瓨绌块€?");
    }

    private List<String> legacyKeywordsForQuestion(String question) {
        if (question.contains("Kubernetes")) {
            return List.of("Kubernetes");
        }
        if (question.contains("JWT") || question.contains("token") || question.contains("退出后")) {
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
        if (keywords.contains("Kubernetes")
                || keywords.contains("Kafka")
                || keywords.contains("Elasticsearch")
                || keywords.contains("Docker")
                || keywords.contains("Spring Cloud Gateway")) {
            return List.of();
        }
        if (keywords.contains("JWT")) {
            return List.of(chunk(18L, "JWT 退出登录与 Redis 黑名单", "JWT logout uses a Redis blacklist."));
        }
        if (keywords.contains("MYSQL_INDEX")) {
            return List.of(chunk(23L, "MySQL 索引优化", "Composite and covering indexes reduce scanned rows."));
        }
        if (keywords.contains("MYSQL_TRANSACTION")) {
            return List.of(chunk(24L, "MySQL 事务隔离级别", "Repeatable read uses MVCC snapshots and next-key locks."));
        }
        if (keywords.contains("THREAD_POOL")) {
            return List.of(chunk(25L, "线程池核心参数与拒绝策略", "Thread pool sizing depends on core threads, queue capacity, and rejection policy."));
        }
        if (keywords.contains("SPRING_BEAN")) {
            return List.of(chunk(26L, "Spring Bean 生命周期与 IoC", "Spring creates beans through instantiation, dependency injection, and post processors."));
        }
        if (keywords.contains("MYSQL_SLOW_QUERY")) {
            return List.of(chunk(27L, "MySQL 慢查询与 explain", "Slow SQL analysis starts from slow logs and explain plans."));
        }
        if (keywords.contains("REDIS_PERSISTENCE")) {
            return List.of(chunk(28L, "Redis 持久化 RDB/AOF", "RDB snapshots and AOF logs trade recovery time for write overhead."));
        }
        if (keywords.contains("REDIS_LOCK")) {
            return List.of(chunk(29L, "Redis 分布式锁", "Redis distributed locks need unique tokens and atomic release scripts."));
        }
        if (keywords.contains("HTTP_TCP")) {
            return List.of(chunk(30L, "HTTP 与 TCP 基础", "HTTP is an application protocol that commonly runs over TCP."));
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
        if (keywords.contains("Kubernetes")
                || keywords.contains("Kafka")
                || keywords.contains("Elasticsearch")
                || keywords.contains("Docker")
                || keywords.contains("Spring Cloud Gateway")) {
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
                mock(HybridRetrievalStrategy.class),
                mock(KeywordRetrievalStrategy.class),
                mock(RetrievalKeywordService.class),
                rerankRouter()
        );
    }

    private RerankClientRouter rerankRouter() {
        AiProperties aiProperties = new AiProperties();
        return new RerankClientRouter(
                aiProperties,
                List.of(new NoneRerankClient(), new RemoteRerankClient(aiProperties))
        );
    }
}
