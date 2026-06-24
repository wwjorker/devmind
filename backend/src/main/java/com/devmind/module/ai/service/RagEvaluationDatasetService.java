package com.devmind.module.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devmind.module.ai.entity.AiAskLog;
import com.devmind.module.ai.mapper.AiAskLogMapper;
import com.devmind.module.ai.vo.RagEvaluationCaseResponse;
import com.devmind.module.ai.vo.RagEvaluationDatasetResponse;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RagEvaluationDatasetService {

    private static final List<EvaluationCaseDefinition> CASES = List.of(
            new EvaluationCaseDefinition(
                    "redis-cache-penetration-basic",
                    "redis",
                    "How should I explain Redis cache penetration in an interview?",
                    List.of("Redis", "cache", "penetration"),
                    "Should mention repeated misses for non-existing keys, empty-value caching, parameter validation, rate limiting, and difference from breakdown or avalanche.",
                    "Expected evidence should come from Redis cache penetration notes or bug reviews.",
                    "normal_retrieval"
            ),
            new EvaluationCaseDefinition(
                    "redis-cache-penetration-cn",
                    "redis",
                    "Redis 缓存穿透怎么解决？",
                    List.of("Redis", "缓存穿透", "空值", "限流"),
                    "应该说明缓存空值、过滤非法参数、布隆过滤器或限流，并说明目标是保护数据库。",
                    "Expected evidence should come from Chinese or English Redis cache penetration chunks.",
                    "multilingual_retrieval"
            ),
            new EvaluationCaseDefinition(
                    "jwt-logout-blacklist",
                    "security",
                    "Why does JWT logout need a Redis blacklist?",
                    List.of("JWT", "logout", "Redis", "blacklist"),
                    "Should explain JWT statelessness, why an unexpired token may remain valid, and how Redis TTL blacklists make logout effective.",
                    "Expected evidence should come from JWT or Redis blacklist design notes.",
                    "backend_design"
            ),
            new EvaluationCaseDefinition(
                    "flyway-migration",
                    "database",
                    "What problem does Flyway migration solve in this project?",
                    List.of("Flyway", "migration", "database"),
                    "Should mention versioned schema changes, automatic migration on startup, and avoiding manual SQL drift across environments.",
                    "Expected evidence should come from database migration or local setup notes.",
                    "backend_design"
            ),
            new EvaluationCaseDefinition(
                    "llm-provider-abstraction",
                    "ai_engineering",
                    "Why does DevMind use an LlmClient interface instead of directly calling DeepSeek?",
                    List.of("LlmClient", "DeepSeek", "Mock", "provider"),
                    "Should mention provider switching, local mock testing, cost control, and avoiding hard coupling to one model vendor.",
                    "Expected evidence should come from LLM provider abstraction notes or architecture docs.",
                    "ai_engineering"
            ),
            new EvaluationCaseDefinition(
                    "no-context-fallback",
                    "rag",
                    "What should DevMind do when retrieval returns no chunks?",
                    List.of("fallback", "retrieval", "chunks"),
                    "Should explain that the system should avoid unsupported answers, skip model calls when appropriate, and tell the user the knowledge base lacks enough information.",
                    "Expected evidence should come from RAG fallback or hallucination-control notes.",
                    "hallucination_control"
            ),
            new EvaluationCaseDefinition(
                    "unknown-kubernetes-fallback",
                    "negative_case",
                    "What is Kubernetes pod eviction policy?",
                    List.of("Kubernetes", "pod", "eviction"),
                    "If no Kubernetes notes exist, the expected behavior is a no-context fallback instead of a fabricated answer.",
                    "Expected evidence is zero retrieved chunks unless Kubernetes notes have been added.",
                    "no_context_negative_case"
            ),
            new EvaluationCaseDefinition(
                    "rag-evaluation-purpose",
                    "evaluation",
                    "How do you know whether the RAG answer is good?",
                    List.of("RAG", "evaluation", "bad case", "hit rate"),
                    "Should mention evaluation questions, expected answers, retrieved chunks, bad-case feedback, and future metrics such as hit rate or MRR.",
                    "Expected evidence should come from evaluation or bad-case notes.",
                    "evaluation_reasoning"
            )
    );

    private final AiAskLogMapper askLogMapper;

    public RagEvaluationDatasetService(AiAskLogMapper askLogMapper) {
        this.askLogMapper = askLogMapper;
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

    private double roundToFourDecimals(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private record EvaluationCaseDefinition(String caseId,
                                            String category,
                                            String question,
                                            List<String> expectedKeywords,
                                            String expectedAnswer,
                                            String expectedEvidence,
                                            String riskType) {
    }
}
