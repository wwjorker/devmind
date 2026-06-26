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
                    "面试中应该如何解释 Redis 缓存穿透？",
                    List.of("Redis", "cache", "penetration"),
                    "应该说明缓存穿透是请求不存在的数据导致缓存未命中并反复打到数据库，解决方案包括缓存空值、参数校验、布隆过滤器或限流，并能区分缓存击穿和缓存雪崩。",
                    "证据应该来自 Redis 缓存穿透笔记、bug 复盘或后端缓存设计文档。",
                    "normal_retrieval"
            ),
            new EvaluationCaseDefinition(
                    "redis-cache-penetration-cn",
                    "redis",
                    "Redis 缓存穿透怎么解决？",
                    List.of("Redis", "缓存穿透", "空值", "限流"),
                    "应该说明缓存空值、过滤非法参数、布隆过滤器或限流，并说明目标是保护数据库免受重复 miss 冲击。",
                    "证据应该来自中文或英文 Redis 缓存穿透 chunk。",
                    "multilingual_retrieval"
            ),
            new EvaluationCaseDefinition(
                    "jwt-logout-blacklist",
                    "security",
                    "JWT 退出登录为什么需要 Redis 黑名单？",
                    List.of("JWT", "logout", "Redis", "blacklist"),
                    "应该说明 JWT 无状态导致服务端默认不保存会话，未过期 token 退出后仍可能可用；把 token 放入 Redis 黑名单并设置剩余 TTL，可以在认证过滤器中拦截已退出 token。",
                    "证据应该来自 JWT、Redis 黑名单或登录认证设计笔记。",
                    "backend_design"
            ),
            new EvaluationCaseDefinition(
                    "flyway-migration",
                    "database",
                    "这个项目里 Flyway migration 解决了什么问题？",
                    List.of("Flyway", "migration", "database"),
                    "应该说明 Flyway 用版本化脚本管理表结构变更，应用启动时自动迁移，避免不同环境手动执行 SQL 导致结构漂移。",
                    "证据应该来自数据库迁移、本地启动或工程化初始化文档。",
                    "backend_design"
            ),
            new EvaluationCaseDefinition(
                    "llm-provider-abstraction",
                    "ai_engineering",
                    "为什么 DevMind 要抽象 LlmClient，而不是直接调用 DeepSeek？",
                    List.of("LlmClient", "DeepSeek", "Mock", "provider"),
                    "应该说明 LlmClient 可以隔离模型供应商，支持 Mock/DeepSeek 切换、本地测试、成本控制，并避免业务服务和某个厂商 API 强耦合。",
                    "证据应该来自 LLM Provider 抽象、架构说明或 AI Ask 主链路文档。",
                    "ai_engineering"
            ),
            new EvaluationCaseDefinition(
                    "no-context-fallback",
                    "rag",
                    "检索不到 chunk 时 DevMind 应该怎么处理？",
                    List.of("fallback", "retrieval", "chunks"),
                    "应该说明系统不能强行编答案，应返回知识库资料不足的兜底提示；必要时跳过模型调用，减少幻觉和无效成本。",
                    "证据应该来自 RAG 兜底、幻觉控制或无上下文处理文档。",
                    "hallucination_control"
            ),
            new EvaluationCaseDefinition(
                    "unknown-kubernetes-fallback",
                    "negative_case",
                    "Kubernetes Pod 驱逐策略是什么？",
                    List.of("Kubernetes", "pod", "eviction"),
                    "如果知识库没有 Kubernetes 笔记，期望行为是返回无上下文兜底，而不是编造答案。",
                    "除非已经添加 Kubernetes 笔记，否则期望证据是召回 chunk 数为 0。",
                    "no_context_negative_case"
            ),
            new EvaluationCaseDefinition(
                    "rag-evaluation-purpose",
                    "evaluation",
                    "怎么判断 RAG 回答质量是否足够好？",
                    List.of("RAG", "evaluation", "bad case", "hit rate"),
                    "应该说明可以用标准问题、期望答案、召回 chunks、bad case feedback 评估链路质量，后续再补 hit rate、MRR 等指标。",
                    "证据应该来自 evaluation dataset、bad case 或 RAG 质量分析文档。",
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
