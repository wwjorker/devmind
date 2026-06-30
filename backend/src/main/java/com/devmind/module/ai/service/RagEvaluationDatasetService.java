package com.devmind.module.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devmind.module.ai.entity.AiAskLog;
import com.devmind.module.ai.mapper.AiAskLogMapper;
import com.devmind.module.ai.vo.RagEvaluationCaseResponse;
import com.devmind.module.ai.vo.RagEvaluationDatasetResponse;
import com.devmind.module.ai.vo.RagRetrievalEvaluationCaseResponse;
import com.devmind.module.ai.vo.RagRetrievalEvaluationResponse;
import com.devmind.module.search.strategy.KeywordRetrievalStrategy;
import com.devmind.module.search.strategy.RetrievalStrategy;
import com.devmind.module.search.vo.ChunkSearchResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class RagEvaluationDatasetService {

    private static final int RETRIEVAL_EVALUATION_K = 3;
    private static final int RETRIEVAL_EVALUATION_LIMIT = 5;
    private static final String RELEVANCE_MODE = "gold-document-title";

    private static final List<EvaluationCaseDefinition> CASES = List.of(
            new EvaluationCaseDefinition(
                    "redis-cache-penetration-basic",
                    "redis",
                    "面试中应该如何解释 Redis 缓存穿透？",
                    List.of("Redis 缓存穿透复盘", "Redis cache penetration review", "Redis cache penetration review updated", "redis-cache-penetration"),
                    List.of("Redis", "cache", "penetration"),
                    "应该说明缓存穿透是请求不存在的数据导致缓存未命中并反复打到数据库，解决方案包括缓存空值、参数校验、布隆过滤器或限流，并能区分缓存击穿和缓存雪崩。",
                    "证据应该来自 Redis 缓存穿透笔记、bug 复盘或后端缓存设计文档。",
                    "normal_retrieval"
            ),
            new EvaluationCaseDefinition(
                    "redis-cache-penetration-cn",
                    "redis",
                    "Redis 缓存穿透怎么解决？",
                    List.of("Redis 缓存穿透复盘", "Redis cache penetration review", "Redis cache penetration review updated", "redis-cache-penetration"),
                    List.of("Redis", "缓存穿透", "空值", "限流"),
                    "应该说明缓存空值、过滤非法参数、布隆过滤器或限流，并说明目标是保护数据库免受重复 miss 冲击。",
                    "证据应该来自中文或英文 Redis 缓存穿透 chunk。",
                    "multilingual_retrieval"
            ),
            new EvaluationCaseDefinition(
                    "jwt-logout-blacklist",
                    "security",
                    "JWT 退出登录为什么需要 Redis 黑名单？",
                    List.of("JWT 退出登录与 Redis 黑名单"),
                    List.of("JWT", "logout", "Redis", "blacklist"),
                    "应该说明 JWT 无状态导致服务端默认不保存会话，未过期 token 退出后仍可能可用；把 token 放入 Redis 黑名单并设置剩余 TTL，可以在认证过滤器中拦截已退出 token。",
                    "证据应该来自 JWT、Redis 黑名单或登录认证设计笔记。",
                    "backend_design"
            ),
            new EvaluationCaseDefinition(
                    "redis-missing-key-db-pressure",
                    "redis",
                    "查不存在的 key 一直打到 MySQL，怎么避免数据库被打穿？",
                    List.of("Redis 缓存穿透复盘", "Redis cache penetration review", "Redis cache penetration review updated", "redis-cache-penetration"),
                    List.of("key", "MySQL", "TTL", "限流"),
                    "应该识别这是缓存穿透场景：请求查询不存在的数据，缓存无法命中并反复打到数据库；解决方案包括缓存空值并设置较短 TTL、参数校验、布隆过滤器和接口限流。",
                    "证据应该来自 Redis 缓存穿透复盘或后端缓存设计笔记；这个 case 故意不用“缓存穿透”作为问题主表达，用来检查改写问题下的召回能力。",
                    "lexical_mismatch_retrieval"
            ),
            new EvaluationCaseDefinition(
                    "token-still-valid-after-logout",
                    "security",
                    "用户退出后旧 token 还没过期，后端怎么拦截？",
                    List.of("JWT 退出登录与 Redis 黑名单"),
                    List.of("token", "Redis", "TTL", "黑名单"),
                    "应该说明 JWT 是无状态的，服务端默认不会保存会话；退出登录后需要把旧 token 放入 Redis 黑名单，并设置为 token 剩余有效期 TTL，认证过滤器发现黑名单命中后拒绝请求。",
                    "证据应该来自 JWT 退出登录与 Redis 黑名单文档；这个 case 故意不直接问“JWT 黑名单”，用于检查登录退出问题的轻度改写召回。",
                    "lexical_mismatch_retrieval"
            ),
            new EvaluationCaseDefinition(
                    "flyway-migration",
                    "database",
                    "这个项目里 Flyway migration 解决了什么问题？",
                    List.of("Flyway migration 数据库迁移"),
                    List.of("Flyway", "migration", "database"),
                    "应该说明 Flyway 用版本化脚本管理表结构变更，应用启动时自动迁移，避免不同环境手动执行 SQL 导致结构漂移。",
                    "证据应该来自数据库迁移、本地启动或工程化初始化文档。",
                    "backend_design"
            ),
            new EvaluationCaseDefinition(
                    "llm-provider-abstraction",
                    "ai_engineering",
                    "为什么 DevMind 要抽象 LlmClient，而不是直接调用 DeepSeek？",
                    List.of("LlmClient 与 LLM Provider 抽象"),
                    List.of("LlmClient", "DeepSeek", "Mock", "provider"),
                    "应该说明 LlmClient 可以隔离模型供应商，支持 Mock/DeepSeek 切换、本地测试、成本控制，并避免业务服务和某个厂商 API 强耦合。",
                    "证据应该来自 LLM Provider 抽象、架构说明或 AI Ask 主链路文档。",
                    "ai_engineering"
            ),
            new EvaluationCaseDefinition(
                    "no-context-fallback",
                    "rag",
                    "检索不到 chunk 时 DevMind 应该怎么处理？",
                    List.of("RAG 无上下文兜底"),
                    List.of("fallback", "retrieval", "chunks"),
                    "应该说明系统不能强行编答案，应返回知识库资料不足的兜底提示；必要时跳过模型调用，减少幻觉和无效成本。",
                    "证据应该来自 RAG 兜底、幻觉控制或无上下文处理文档。",
                    "hallucination_control"
            ),
            new EvaluationCaseDefinition(
                    "unknown-kubernetes-fallback",
                    "negative_case",
                    "Kubernetes Pod 驱逐策略是什么？",
                    List.of(),
                    List.of("Kubernetes", "pod", "eviction"),
                    "如果知识库没有 Kubernetes 笔记，期望行为是返回无上下文兜底，而不是编造答案。",
                    "除非已经添加 Kubernetes 笔记，否则期望证据是召回 chunk 数为 0。",
                    "no_context_negative_case"
            ),
            new EvaluationCaseDefinition(
                    "rag-evaluation-purpose",
                    "evaluation",
                    "怎么判断 RAG 回答质量是否足够好？",
                    List.of("RAG 回答质量评估"),
                    List.of("RAG", "evaluation", "bad case", "hit rate"),
                    "应该说明可以用标准问题、期望答案、召回 chunks、bad case feedback、Hit@K 和 MRR 评估链路质量。",
                    "证据应该来自 evaluation dataset、bad case 或 RAG 质量分析文档。",
                    "evaluation_reasoning"
            )
    );

    private final AiAskLogMapper askLogMapper;
    private final RetrievalStrategy retrievalStrategy;
    private final KeywordRetrievalStrategy keywordRetrievalStrategy;
    private final RetrievalKeywordService retrievalKeywordService;

    public RagEvaluationDatasetService(AiAskLogMapper askLogMapper,
                                       RetrievalStrategy retrievalStrategy,
                                       KeywordRetrievalStrategy keywordRetrievalStrategy,
                                       RetrievalKeywordService retrievalKeywordService) {
        this.askLogMapper = askLogMapper;
        this.retrievalStrategy = retrievalStrategy;
        this.keywordRetrievalStrategy = keywordRetrievalStrategy;
        this.retrievalKeywordService = retrievalKeywordService;
    }

    public RagEvaluationDatasetResponse dataset(Long userId) {
        Map<String, AiAskLog> latestLogByQuestion = loadLatestLogByQuestion(userId);
        List<RagEvaluationCaseResponse> caseResponses = CASES.stream()
                .map(caseDefinition -> toResponse(caseDefinition, latestLogByQuestion.get(caseDefinition.question())))
                .toList();

        int coveredCaseCount = (int) caseResponses.stream()
                .filter(response -> Boolean.TRUE.equals(response.getCovered()))
                .count();
        double coverageRate = caseResponses.isEmpty()
                ? 0.0
                : roundToFourDecimals((double) coveredCaseCount / caseResponses.size());

        return new RagEvaluationDatasetResponse(
                caseResponses.size(),
                coveredCaseCount,
                coverageRate,
                caseResponses
        );
    }

    public RagRetrievalEvaluationResponse retrievalEvaluation(Long userId) {
        EvaluationRun currentRun = evaluateWithStrategy(userId, retrievalStrategy);
        EvaluationRun baselineRun = evaluateWithStrategy(userId, keywordRetrievalStrategy);

        return new RagRetrievalEvaluationResponse(
                currentRun.caseResponses().size(),
                currentRun.passedCaseCount(),
                currentRun.passRate(),
                currentRun.positiveCaseCount(),
                RETRIEVAL_EVALUATION_K,
                RETRIEVAL_EVALUATION_LIMIT,
                retrievalStrategy.strategyName(),
                retrievalStrategy.description(),
                keywordRetrievalStrategy.strategyName(),
                keywordRetrievalStrategy.description(),
                RELEVANCE_MODE,
                currentRun.hitAtK(),
                currentRun.mrr(),
                baselineRun.passedCaseCount(),
                baselineRun.passRate(),
                baselineRun.hitAtK(),
                baselineRun.mrr(),
                roundToFourDecimals(currentRun.hitAtK() - baselineRun.hitAtK()),
                roundToFourDecimals(currentRun.mrr() - baselineRun.mrr()),
                currentRun.caseResponses()
        );
    }

    private EvaluationRun evaluateWithStrategy(Long userId, RetrievalStrategy strategy) {
        List<RagRetrievalEvaluationCaseResponse> caseResponses = CASES.stream()
                .map(caseDefinition -> evaluateRetrievalCase(userId, caseDefinition, strategy))
                .toList();

        int passedCaseCount = (int) caseResponses.stream()
                .filter(response -> Boolean.TRUE.equals(response.getPassed()))
                .count();
        double passRate = caseResponses.isEmpty()
                ? 0.0
                : roundToFourDecimals((double) passedCaseCount / caseResponses.size());
        List<RagRetrievalEvaluationCaseResponse> positiveCases = caseResponses.stream()
                .filter(response -> !Boolean.TRUE.equals(response.getExpectedNoContext()))
                .toList();
        int positiveCaseCount = positiveCases.size();
        int hitCount = (int) positiveCases.stream()
                .filter(response -> Boolean.TRUE.equals(response.getHitAtK()))
                .count();
        double hitAtK = positiveCaseCount == 0
                ? 0.0
                : roundToFourDecimals((double) hitCount / positiveCaseCount);
        double mrr = positiveCaseCount == 0
                ? 0.0
                : roundToFourDecimals(positiveCases.stream()
                .map(RagRetrievalEvaluationCaseResponse::getReciprocalRank)
                .mapToDouble(rank -> rank == null ? 0.0 : rank)
                .sum() / positiveCaseCount);

        return new EvaluationRun(caseResponses, passedCaseCount, passRate, positiveCaseCount, hitAtK, mrr);
    }

    private Map<String, AiAskLog> loadLatestLogByQuestion(Long userId) {
        List<String> questions = CASES.stream()
                .map(EvaluationCaseDefinition::question)
                .toList();
        if (questions.isEmpty()) {
            return Map.of();
        }

        List<AiAskLog> logs = askLogMapper.selectList(new LambdaQueryWrapper<AiAskLog>()
                .eq(AiAskLog::getUserId, userId)
                .in(AiAskLog::getQuestion, questions)
                .orderByDesc(AiAskLog::getCreatedAt)
                .orderByDesc(AiAskLog::getId));

        Map<String, AiAskLog> latestLogByQuestion = new LinkedHashMap<>();
        for (AiAskLog log : logs) {
            latestLogByQuestion.putIfAbsent(log.getQuestion(), log);
        }
        return latestLogByQuestion;
    }

    private RagEvaluationCaseResponse toResponse(EvaluationCaseDefinition caseDefinition, AiAskLog latestLog) {
        boolean covered = latestLog != null;
        return new RagEvaluationCaseResponse(
                caseDefinition.caseId(),
                caseDefinition.category(),
                caseDefinition.question(),
                caseDefinition.expectedKeywords(),
                caseDefinition.expectedAnswer(),
                caseDefinition.expectedEvidence(),
                caseDefinition.riskType(),
                covered,
                covered ? latestLog.getId() : null,
                covered ? latestLog.getStatus() : null,
                covered ? latestLog.getRetrievedChunkCount() : null,
                covered ? latestLog.getRetrievedChunkIds() : null,
                covered ? latestLog.getCreatedAt() : null
        );
    }

    private RagRetrievalEvaluationCaseResponse evaluateRetrievalCase(Long userId,
                                                                    EvaluationCaseDefinition caseDefinition,
                                                                    RetrievalStrategy strategy) {
        List<String> queryKeywords = retrievalKeywordService.resolveKeywords(caseDefinition.question());
        List<ChunkSearchResponse> chunks = strategy.retrieve(userId, queryKeywords, RETRIEVAL_EVALUATION_LIMIT);
        boolean expectedNoContext = "no_context_negative_case".equals(caseDefinition.riskType());
        List<String> matchedKeywords = matchedExpectedKeywords(caseDefinition.expectedKeywords(), chunks);
        List<String> missingKeywords = missingExpectedKeywords(caseDefinition.expectedKeywords(), matchedKeywords);
        Integer firstRelevantRank = expectedNoContext ? null : firstRelevantRank(caseDefinition.relevantDocumentTitles(), chunks);
        Boolean hitAtK = expectedNoContext
                ? chunks.isEmpty()
                : firstRelevantRank != null && firstRelevantRank <= RETRIEVAL_EVALUATION_K;
        Double reciprocalRank = expectedNoContext
                ? null
                : firstRelevantRank == null ? 0.0 : roundToFourDecimals(1.0 / firstRelevantRank);
        boolean passed = expectedNoContext ? chunks.isEmpty() : Boolean.TRUE.equals(hitAtK);

        return new RagRetrievalEvaluationCaseResponse(
                caseDefinition.caseId(),
                caseDefinition.category(),
                caseDefinition.question(),
                caseDefinition.expectedKeywords(),
                caseDefinition.relevantDocumentTitles(),
                queryKeywords,
                matchedKeywords,
                missingKeywords,
                caseDefinition.expectedEvidence(),
                caseDefinition.riskType(),
                passed,
                expectedNoContext,
                chunks.size(),
                firstRelevantRank,
                hitAtK,
                reciprocalRank,
                topChunkIds(chunks),
                topDocumentTitles(chunks),
                retrievalNote(expectedNoContext, chunks, matchedKeywords, firstRelevantRank, hitAtK)
        );
    }

    private List<String> matchedExpectedKeywords(List<String> expectedKeywords, List<ChunkSearchResponse> chunks) {
        String searchableText = chunks.stream()
                .map(this::searchableText)
                .reduce("", (left, right) -> left + "\n" + right)
                .toLowerCase(Locale.ROOT);

        List<String> matched = new ArrayList<>();
        for (String keyword : expectedKeywords) {
            if (searchableText.contains(keyword.toLowerCase(Locale.ROOT))) {
                matched.add(keyword);
            }
        }
        return matched;
    }

    private Integer firstRelevantRank(List<String> relevantDocumentTitles, List<ChunkSearchResponse> chunks) {
        for (int index = 0; index < chunks.size(); index++) {
            if (isGoldDocumentHit(relevantDocumentTitles, chunks.get(index))) {
                return index + 1;
            }
        }
        return null;
    }

    private boolean isGoldDocumentHit(List<String> relevantDocumentTitles, ChunkSearchResponse chunk) {
        String documentTitle = normalizeTitle(chunk.getDocumentTitle());
        return relevantDocumentTitles.stream()
                .map(this::normalizeTitle)
                .anyMatch(documentTitle::equals);
    }

    private String searchableText(ChunkSearchResponse chunk) {
        return String.join(" ",
                safeText(chunk.getDocumentTitle()),
                safeText(chunk.getSourceType()),
                safeText(chunk.getTags()),
                safeText(chunk.getContent()));
    }

    private List<String> missingExpectedKeywords(List<String> expectedKeywords, List<String> matchedKeywords) {
        Set<String> matchedSet = new LinkedHashSet<>(matchedKeywords);
        return expectedKeywords.stream()
                .filter(keyword -> !matchedSet.contains(keyword))
                .toList();
    }

    private List<Long> topChunkIds(List<ChunkSearchResponse> chunks) {
        return chunks.stream()
                .map(ChunkSearchResponse::getChunkId)
                .toList();
    }

    private List<String> topDocumentTitles(List<ChunkSearchResponse> chunks) {
        return chunks.stream()
                .map(ChunkSearchResponse::getDocumentTitle)
                .filter(title -> title != null && !title.isBlank())
                .distinct()
                .toList();
    }

    private String retrievalNote(boolean expectedNoContext,
                                 List<ChunkSearchResponse> chunks,
                                 List<String> matchedKeywords,
                                 Integer firstRelevantRank,
                                 Boolean hitAtK) {
        if (expectedNoContext && chunks.isEmpty()) {
            return "Expected no-context fallback: no chunks were retrieved.";
        }
        if (expectedNoContext) {
            return "Needs review: this negative case retrieved chunks and may cause unsupported answers.";
        }
        if (chunks.isEmpty()) {
            return "Needs review: no chunks were retrieved for a positive evaluation case.";
        }
        if (Boolean.TRUE.equals(hitAtK)) {
            return "Hit@" + RETRIEVAL_EVALUATION_K + " passed: the first gold document chunk is ranked #" + firstRelevantRank + ".";
        }
        if (firstRelevantRank != null) {
            return "Needs review: a gold document chunk was found at rank #" + firstRelevantRank
                    + ", but it did not enter Top " + RETRIEVAL_EVALUATION_K + ".";
        }
        if (matchedKeywords.isEmpty()) {
            return "Needs review: retrieved chunks exist, but no gold document chunk was retrieved and none of the expected keywords were found.";
        }
        return "Needs review: expected keywords were found, but no gold document chunk was retrieved.";
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private String normalizeTitle(String value) {
        return safeText(value).trim().toLowerCase(Locale.ROOT);
    }

    private double roundToFourDecimals(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private record EvaluationCaseDefinition(String caseId,
                                            String category,
                                            String question,
                                            List<String> relevantDocumentTitles,
                                            List<String> expectedKeywords,
                                            String expectedAnswer,
                                            String expectedEvidence,
                                            String riskType) {
    }

    private record EvaluationRun(List<RagRetrievalEvaluationCaseResponse> caseResponses,
                                 int passedCaseCount,
                                 double passRate,
                                 int positiveCaseCount,
                                 double hitAtK,
                                 double mrr) {
    }
}
