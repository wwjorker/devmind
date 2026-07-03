package com.devmind.module.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devmind.module.ai.entity.AiAskLog;
import com.devmind.module.ai.mapper.AiAskLogMapper;
import com.devmind.module.ai.vo.RagEvaluationCaseResponse;
import com.devmind.module.ai.vo.RagEvaluationDatasetResponse;
import com.devmind.module.ai.vo.RagRetrievalEvaluationCaseResponse;
import com.devmind.module.ai.vo.RagRetrievalEvaluationResponse;
import com.devmind.module.ai.vo.RagRetrievalStrategyEvaluationResponse;
import com.devmind.module.search.rerank.RerankClientRouter;
import com.devmind.module.search.strategy.HybridRetrievalStrategy;
import com.devmind.module.search.strategy.KeywordRetrievalStrategy;
import com.devmind.module.search.strategy.RetrievalStrategy;
import com.devmind.module.search.vo.ChunkSearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(RagEvaluationDatasetService.class);

    private static final int RETRIEVAL_EVALUATION_K = 3;
    private static final int RETRIEVAL_EVALUATION_LIMIT = 5;
    private static final String RELEVANCE_MODE = "gold-document-title";
    private static final String LOCAL_SPARSE_PROVIDER = "local-sparse-vector";
    private static final String REMOTE_DENSE_PROVIDER = "remote-dense";
    private static final String REMOTE_RERANK_PROVIDER = "remote-rerank";
    private static final String STATUS_AVAILABLE = "available";
    private static final String STATUS_UNAVAILABLE = "unavailable";

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
            ),
            new EvaluationCaseDefinition(
                    "redis-empty-value-cache-plain",
                    "redis",
                    "查不存在的数据时怎样别让请求每次都落到数据库？",
                    List.of("Redis 缓存穿透复盘", "Redis cache penetration review", "Redis cache penetration review updated", "redis-cache-penetration"),
                    List.of("Redis", "空值缓存", "数据库", "限流"),
                    "应该识别这是缓存穿透场景，说明空值缓存、短 TTL、参数校验、限流或布隆过滤器等做法。",
                    "证据应该来自 Redis 缓存穿透复盘文档；这个 case 不直接出现“缓存穿透”，用于检验口语化改写下的召回。",
                    "lexical_mismatch_retrieval"
            ),
            new EvaluationCaseDefinition(
                    "jwt-old-token-block-plain",
                    "security",
                    "退出后旧令牌还能访问接口，服务端要怎么拦？",
                    List.of("JWT 退出登录与 Redis 黑名单"),
                    List.of("JWT", "token", "Redis", "TTL"),
                    "应该说明 JWT 无状态，退出后需要把旧 token 写入 Redis 黑名单，并用剩余有效期作为 TTL。",
                    "证据应该来自 JWT 退出登录与 Redis 黑名单文档；这个 case 用“旧令牌”替代 JWT 黑名单直问。",
                    "lexical_mismatch_retrieval"
            ),
            new EvaluationCaseDefinition(
                    "flyway-manual-sql-drift",
                    "database",
                    "每个环境手动改表结构容易不一致，这个项目怎么避免？",
                    List.of("Flyway migration 数据库迁移"),
                    List.of("Flyway", "migration", "schema", "version"),
                    "应该说明用 Flyway migration 管理表结构版本，应用启动时自动执行未运行的迁移脚本，减少环境漂移。",
                    "证据应该来自 Flyway migration 数据库迁移文档；这个 case 不直接问 Flyway，而是描述手动 SQL 的痛点。",
                    "lexical_mismatch_retrieval"
            ),
            new EvaluationCaseDefinition(
                    "llm-mock-provider-local-test",
                    "ai_engineering",
                    "为什么本地测试时不用真的请求 DeepSeek，也能跑通 AI 问答流程？",
                    List.of("LlmClient 与 LLM Provider 抽象"),
                    List.of("LlmClient", "Mock", "DeepSeek", "provider"),
                    "应该说明 LlmClient 抽象隔离了模型供应商，本地可以走 Mock provider，真实环境再切到 DeepSeek。",
                    "证据应该来自 LlmClient 与 LLM Provider 抽象文档；这个 case 从测试成本角度追问 provider 抽象。",
                    "lexical_mismatch_retrieval"
            ),
            new EvaluationCaseDefinition(
                    "rag-no-evidence-dont-answer",
                    "rag",
                    "知识库里没资料时系统为什么不应该硬编答案？",
                    List.of("RAG 无上下文兜底"),
                    List.of("RAG", "fallback", "hallucination", "chunks"),
                    "应该说明没有检索到有效 chunk 时应返回无上下文兜底，避免幻觉和无效 token 成本。",
                    "证据应该来自 RAG 无上下文兜底文档；这个 case 用“硬编答案”描述幻觉风险。",
                    "lexical_mismatch_retrieval"
            ),
            new EvaluationCaseDefinition(
                    "redis-bloom-filter-wording",
                    "redis",
                    "布隆过滤器在缓存查询链路里能挡住哪类异常流量？",
                    List.of("Redis 缓存穿透复盘", "Redis cache penetration review", "Redis cache penetration review updated", "redis-cache-penetration"),
                    List.of("Redis", "布隆过滤器", "非法参数", "miss"),
                    "应该说明布隆过滤器可提前拦截明显不存在或高风险的查询，减少缓存 miss 和数据库压力。",
                    "证据应该来自 Redis 缓存穿透复盘文档；这个 case 是同一主题的另一种表达。",
                    "synonym_retrieval"
            ),
            new EvaluationCaseDefinition(
                    "jwt-blacklist-ttl-wording",
                    "security",
                    "退出登录时黑名单里的 token 应该保存多久？",
                    List.of("JWT 退出登录与 Redis 黑名单"),
                    List.of("JWT", "blacklist", "TTL", "logout"),
                    "应该说明黑名单 entry 的 TTL 应等于 token 剩余有效期，过期后无需继续保存。",
                    "证据应该来自 JWT 退出登录与 Redis 黑名单文档；这个 case 聚焦 TTL 细节。",
                    "synonym_retrieval"
            ),
            new EvaluationCaseDefinition(
                    "flyway-versioned-script-wording",
                    "database",
                    "数据库变更脚本为什么要带版本号并随应用启动执行？",
                    List.of("Flyway migration 数据库迁移"),
                    List.of("Flyway", "version", "migration", "script"),
                    "应该说明版本化迁移脚本可以记录已执行版本，启动时自动补齐未执行的数据库结构变更。",
                    "证据应该来自 Flyway migration 数据库迁移文档；这个 case 从脚本版本化角度追问。",
                    "synonym_retrieval"
            ),
            new EvaluationCaseDefinition(
                    "llm-provider-switch-wording",
                    "ai_engineering",
                    "如果以后模型供应商从 DeepSeek 换成别家，业务代码为什么不用大改？",
                    List.of("LlmClient 与 LLM Provider 抽象"),
                    List.of("LlmClient", "provider", "DeepSeek", "Mock"),
                    "应该说明业务依赖 LlmClient 接口和 Router，供应商差异留在具体 client 实现中。",
                    "证据应该来自 LlmClient 与 LLM Provider 抽象文档；这个 case 检查供应商切换能力。",
                    "synonym_retrieval"
            ),
            new EvaluationCaseDefinition(
                    "rag-quality-metric-wording",
                    "evaluation",
                    "除了看回答顺不顺，还能用哪些离线指标判断 RAG 检索质量？",
                    List.of("RAG 回答质量评估"),
                    List.of("RAG", "Hit@K", "MRR", "gold label"),
                    "应该说明可以用标准问题、gold label、召回 chunks、Hit@K、MRR 和 bad case feedback 评估质量。",
                    "证据应该来自 RAG 回答质量评估文档；这个 case 是评估主题的另一种问法。",
                    "synonym_retrieval"
            ),
            new EvaluationCaseDefinition(
                    "unknown-kafka-consumer-rebalance",
                    "negative_case",
                    "Kafka consumer rebalance 为什么会变慢？",
                    List.of(),
                    List.of("Kafka", "consumer", "rebalance"),
                    "如果知识库没有 Kafka 笔记，期望行为是返回无上下文兜底，而不是把 Redis、JWT 或 RAG 文档硬套过来。",
                    "除非已经添加 Kafka 文档，否则期望证据是召回 chunk 数为 0。",
                    "no_context_negative_case"
            ),
            new EvaluationCaseDefinition(
                    "unknown-elasticsearch-inverted-index",
                    "negative_case",
                    "Elasticsearch 倒排索引怎么做相关性排序？",
                    List.of(),
                    List.of("Elasticsearch", "inverted index", "ranking"),
                    "如果知识库没有 Elasticsearch 笔记，期望行为是返回无上下文兜底。",
                    "除非已经添加 Elasticsearch 文档，否则期望证据是召回 chunk 数为 0。",
                    "no_context_negative_case"
            ),
            new EvaluationCaseDefinition(
                    "unknown-docker-layer-cache",
                    "negative_case",
                    "Docker 镜像层缓存为什么能加速构建？",
                    List.of(),
                    List.of("Docker", "image", "layer cache"),
                    "如果知识库没有 Docker 笔记，期望行为是返回无上下文兜底。",
                    "除非已经添加 Docker 文档，否则期望证据是召回 chunk 数为 0。",
                    "no_context_negative_case"
            ),
            new EvaluationCaseDefinition(
                    "unknown-spring-cloud-gateway",
                    "negative_case",
                    "Spring Cloud Gateway 的全局过滤器链怎么排序？",
                    List.of(),
                    List.of("Spring Cloud Gateway", "filter", "order"),
                    "如果知识库没有 Spring Cloud Gateway 笔记，期望行为是返回无上下文兜底。",
                    "除非已经添加 Spring Cloud Gateway 文档，否则期望证据是召回 chunk 数为 0。",
                    "no_context_negative_case"
            ),
            new EvaluationCaseDefinition(
                    "hard-negative-redis-vs-flyway-database-pressure",
                    "redis",
                    "数据库压力很大但根因是缓存 miss 反复穿透时，该看哪篇笔记？",
                    List.of("Redis 缓存穿透复盘", "Redis cache penetration review", "Redis cache penetration review updated", "redis-cache-penetration"),
                    List.of("Redis", "database", "miss", "TTL"),
                    "虽然问题包含数据库，但 gold 应该是 Redis 缓存穿透复盘，因为根因是缓存 miss 反复打到数据库。",
                    "证据应该来自 Redis 缓存穿透复盘文档；这个 case 用 database 干扰 Flyway 文档。",
                    "hard_negative_retrieval"
            ),
            new EvaluationCaseDefinition(
                    "hard-negative-jwt-vs-rag-fallback",
                    "security",
                    "用户退出后访问接口被拒绝，这是 fallback 兜底还是 token 黑名单？",
                    List.of("JWT 退出登录与 Redis 黑名单"),
                    List.of("JWT", "token", "blacklist", "fallback"),
                    "问题里有 fallback 干扰词，但正确证据应是 JWT 退出登录与 Redis 黑名单。",
                    "证据应该来自 JWT 退出登录与 Redis 黑名单文档；这个 case 用 fallback 干扰 RAG 兜底文档。",
                    "hard_negative_retrieval"
            ),
            new EvaluationCaseDefinition(
                    "hard-negative-rag-evaluation-vs-llm-provider",
                    "evaluation",
                    "不是换 DeepSeek provider，而是想知道回答质量怎么量化，应该看哪块？",
                    List.of("RAG 回答质量评估"),
                    List.of("RAG", "evaluation", "DeepSeek", "provider"),
                    "问题带有 DeepSeek/provider 干扰词，但 gold 应该是 RAG 回答质量评估。",
                    "证据应该来自 RAG 回答质量评估文档；这个 case 用 provider 干扰 LlmClient 文档。",
                    "hard_negative_retrieval"
            ),
            new EvaluationCaseDefinition(
                    "hard-negative-flyway-vs-llm-router",
                    "database",
                    "Router 能切 provider 不是重点，数据库表结构版本漂移要靠什么管？",
                    List.of("Flyway migration 数据库迁移"),
                    List.of("Flyway", "migration", "provider", "version"),
                    "问题带有 provider/Router 干扰词，但 gold 应该是 Flyway migration 数据库迁移。",
                    "证据应该来自 Flyway migration 数据库迁移文档；这个 case 用 provider 干扰 LlmClient 文档。",
                    "hard_negative_retrieval"
            ),
            new EvaluationCaseDefinition(
                    "mysql-index-covering-query",
                    "database",
                    "联合索引和覆盖索引怎么减少 MySQL 扫描行数？",
                    List.of("MySQL 索引优化"),
                    List.of("MySQL", "索引", "联合索引", "覆盖索引"),
                    "应该说明按查询条件设计联合索引、遵守最左前缀、使用覆盖索引，并通过 explain 验证扫描行数和 key。",
                    "证据应该来自 MySQL 索引优化文档。",
                    "synonym_retrieval"
            ),
            new EvaluationCaseDefinition(
                    "mysql-transaction-repeatable-read",
                    "database",
                    "可重复读为什么能让同一事务里多次查询结果稳定？",
                    List.of("MySQL 事务隔离级别"),
                    List.of("MySQL", "Repeatable Read", "MVCC", "事务"),
                    "应该说明 InnoDB 默认 Repeatable Read 通过 MVCC 提供一致性快照读，并区分脏读、不可重复读和幻读。",
                    "证据应该来自 MySQL 事务隔离级别文档。",
                    "synonym_retrieval"
            ),
            new EvaluationCaseDefinition(
                    "thread-pool-reject-policy",
                    "java_concurrency",
                    "线程池队列满了以后任务会怎么处理？",
                    List.of("线程池核心参数与拒绝策略"),
                    List.of("线程池", "workQueue", "maximumPoolSize", "拒绝策略"),
                    "应该说明任务先用核心线程、再进队列、再扩容到最大线程数，继续满载时触发拒绝策略。",
                    "证据应该来自 线程池核心参数与拒绝策略 文档。",
                    "synonym_retrieval"
            ),
            new EvaluationCaseDefinition(
                    "spring-bean-post-processor",
                    "spring",
                    "Spring 里 AOP 代理通常是在 Bean 生命周期哪个扩展点织入的？",
                    List.of("Spring Bean 生命周期与 IoC"),
                    List.of("Spring", "Bean", "BeanPostProcessor", "AOP"),
                    "应该说明 Bean 生命周期包含实例化、属性填充、Aware 回调、BeanPostProcessor 和初始化，AOP 常通过后置处理器织入代理。",
                    "证据应该来自 Spring Bean 生命周期与 IoC 文档。",
                    "synonym_retrieval"
            ),
            new EvaluationCaseDefinition(
                    "mysql-slow-query-explain-fields",
                    "database",
                    "慢 SQL 排查时 explain 里 type、key、rows、Extra 分别看什么？",
                    List.of("MySQL 慢查询与 explain"),
                    List.of("MySQL", "explain", "type", "rows", "Extra"),
                    "应该说明通过 explain 观察访问类型、命中索引、扫描行数和 filesort/temporary 等额外信息。",
                    "证据应该来自 MySQL 慢查询与 explain 文档。",
                    "synonym_retrieval"
            ),
            new EvaluationCaseDefinition(
                    "redis-persistence-rdb-aof",
                    "redis",
                    "Redis 既想恢复快又想少丢数据，RDB 和 AOF 怎么取舍？",
                    List.of("Redis 持久化 RDB/AOF"),
                    List.of("Redis", "RDB", "AOF", "rewrite"),
                    "应该说明 RDB 是快照、恢复快但可能丢最近数据，AOF 记录写命令、更安全但文件更大，需要 rewrite。",
                    "证据应该来自 Redis 持久化 RDB/AOF 文档。",
                    "synonym_retrieval"
            ),
            new EvaluationCaseDefinition(
                    "redis-lock-safe-release",
                    "redis",
                    "Redis 分布式锁释放时为什么要校验 value？",
                    List.of("Redis 分布式锁"),
                    List.of("Redis", "分布式锁", "SET NX PX", "Lua"),
                    "应该说明加锁用唯一 value，释放时校验 value 后删除，避免误删其他客户端持有的锁。",
                    "证据应该来自 Redis 分布式锁文档。",
                    "synonym_retrieval"
            ),
            new EvaluationCaseDefinition(
                    "http-tcp-layering",
                    "network",
                    "HTTP 和 TCP 分别属于哪一层，HTTPS 又多了什么？",
                    List.of("HTTP 与 TCP 基础"),
                    List.of("HTTP", "TCP", "HTTPS", "TLS"),
                    "应该说明 TCP 是传输层可靠字节流，HTTP 是应用层请求响应协议，HTTPS 在 HTTP 与 TCP 之间加入 TLS。",
                    "证据应该来自 HTTP 与 TCP 基础文档。",
                    "synonym_retrieval"
            ),
            new EvaluationCaseDefinition(
                    "hard-negative-redis-persistence-vs-cache",
                    "redis",
                    "Redis 缓存数据怕宕机丢失时，是看缓存穿透还是 RDB/AOF？",
                    List.of("Redis 持久化 RDB/AOF"),
                    List.of("Redis", "缓存", "RDB", "AOF"),
                    "问题里有缓存干扰词，但 gold 应该是 Redis 持久化 RDB/AOF，因为关注点是宕机后的数据恢复。",
                    "证据应该来自 Redis 持久化 RDB/AOF 文档；这个 case 用缓存干扰 Redis 缓存穿透复盘。",
                    "hard_negative_retrieval"
            ),
            new EvaluationCaseDefinition(
                    "hard-negative-redis-lock-vs-cache",
                    "redis",
                    "Redis 里为了防止并发重复扣库存,应该看缓存穿透还是分布式锁？",
                    List.of("Redis 分布式锁"),
                    List.of("Redis", "并发", "缓存", "分布式锁"),
                    "问题里有缓存干扰词，但 gold 应该是 Redis 分布式锁，因为关注点是并发互斥。",
                    "证据应该来自 Redis 分布式锁文档；这个 case 用缓存干扰 Redis 缓存穿透复盘。",
                    "hard_negative_retrieval"
            ),
            new EvaluationCaseDefinition(
                    "hard-negative-mysql-slow-vs-index",
                    "database",
                    "SQL 很慢但我已经有索引了，下一步应该看索引设计还是 explain 执行计划？",
                    List.of("MySQL 慢查询与 explain"),
                    List.of("MySQL", "索引", "explain", "慢查询"),
                    "问题带有索引干扰词，但 gold 应该是 MySQL 慢查询与 explain，因为重点是慢 SQL 排查路径。",
                    "证据应该来自 MySQL 慢查询与 explain 文档；这个 case 用索引干扰 MySQL 索引优化文档。",
                    "hard_negative_retrieval"
            ),
            new EvaluationCaseDefinition(
                    "hard-negative-mysql-transaction-vs-index",
                    "database",
                    "并发更新时读到的数据前后不一致，这是索引问题还是事务隔离问题？",
                    List.of("MySQL 事务隔离级别"),
                    List.of("MySQL", "索引", "事务", "隔离级别"),
                    "问题带有索引干扰词，但 gold 应该是 MySQL 事务隔离级别，因为关注并发一致性。",
                    "证据应该来自 MySQL 事务隔离级别文档；这个 case 用索引干扰 MySQL 索引优化文档。",
                    "hard_negative_retrieval"
            )
    );

    private final AiAskLogMapper askLogMapper;
    private final HybridRetrievalStrategy hybridRetrievalStrategy;
    private final KeywordRetrievalStrategy keywordRetrievalStrategy;
    private final RetrievalKeywordService retrievalKeywordService;
    private final RerankClientRouter rerankClientRouter;

    public RagEvaluationDatasetService(AiAskLogMapper askLogMapper,
                                       HybridRetrievalStrategy hybridRetrievalStrategy,
                                       KeywordRetrievalStrategy keywordRetrievalStrategy,
                                       RetrievalKeywordService retrievalKeywordService,
                                       RerankClientRouter rerankClientRouter) {
        this.askLogMapper = askLogMapper;
        this.hybridRetrievalStrategy = hybridRetrievalStrategy;
        this.keywordRetrievalStrategy = keywordRetrievalStrategy;
        this.retrievalKeywordService = retrievalKeywordService;
        this.rerankClientRouter = rerankClientRouter;
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
        EvaluationRun baselineRun = evaluateWithStrategy(userId, keywordRetrievalStrategy);
        EvaluationRun sparseRun = evaluateWithHybridProvider(userId, LOCAL_SPARSE_PROVIDER);
        List<RagRetrievalStrategyEvaluationResponse> strategyResults = List.of(
                availableStrategyResult(
                        "keyword-baseline",
                        null,
                        keywordRetrievalStrategy.strategyName(),
                        keywordRetrievalStrategy.description(),
                        baselineRun,
                        baselineRun
                ),
                availableStrategyResult(
                        "sparse-hybrid",
                        LOCAL_SPARSE_PROVIDER,
                        hybridRetrievalStrategy.strategyName(),
                        hybridRetrievalStrategy.description(),
                        sparseRun,
                        baselineRun
                ),
                denseHybridStrategyResult(userId, baselineRun),
                denseHybridRerankStrategyResult(userId, baselineRun)
        );

        return new RagRetrievalEvaluationResponse(
                sparseRun.caseResponses().size(),
                sparseRun.passedCaseCount(),
                sparseRun.passRate(),
                sparseRun.positiveCaseCount(),
                RETRIEVAL_EVALUATION_K,
                RETRIEVAL_EVALUATION_LIMIT,
                hybridRetrievalStrategy.strategyName(),
                hybridRetrievalStrategy.description(),
                keywordRetrievalStrategy.strategyName(),
                keywordRetrievalStrategy.description(),
                RELEVANCE_MODE,
                sparseRun.hitAtK(),
                sparseRun.mrr(),
                baselineRun.passedCaseCount(),
                baselineRun.passRate(),
                baselineRun.hitAtK(),
                baselineRun.mrr(),
                roundToFourDecimals(sparseRun.hitAtK() - baselineRun.hitAtK()),
                roundToFourDecimals(sparseRun.mrr() - baselineRun.mrr()),
                strategyResults,
                sparseRun.caseResponses()
        );
    }

    private EvaluationRun evaluateWithStrategy(Long userId, RetrievalStrategy strategy) {
        return evaluateWithRetriever(userId, strategy::retrieve);
    }

    private EvaluationRun evaluateWithHybridProvider(Long userId, String provider) {
        return evaluateWithRetriever(userId,
                (caseUserId, keywords, limit) -> hybridRetrievalStrategy.retrieveWithEmbeddingProvider(
                        caseUserId,
                        keywords,
                        limit,
                        provider
                ));
    }

    private EvaluationRun evaluateWithRetriever(Long userId, CaseRetriever retriever) {
        List<RagRetrievalEvaluationCaseResponse> caseResponses = CASES.stream()
                .map(caseDefinition -> evaluateRetrievalCase(userId, caseDefinition, retriever))
                .toList();
        return evaluationRun(caseResponses);
    }

    private EvaluationRun evaluateDenseHybridWithRerank(Long userId) {
        List<RagRetrievalEvaluationCaseResponse> caseResponses = CASES.stream()
                .map(caseDefinition -> evaluateRetrievalCase(userId, caseDefinition,
                        (caseUserId, keywords, limit) -> {
                            List<ChunkSearchResponse> candidates = hybridRetrievalStrategy.retrieveWithEmbeddingProvider(
                                    caseUserId,
                                    keywords,
                                    limit,
                                    REMOTE_DENSE_PROVIDER
                            );
                            return rerankClientRouter.clientFor(REMOTE_RERANK_PROVIDER)
                                    .rerank(caseDefinition.question(), candidates, limit);
                        }))
                .toList();
        return evaluationRun(caseResponses);
    }

    private EvaluationRun evaluationRun(List<RagRetrievalEvaluationCaseResponse> caseResponses) {
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

    private RagRetrievalStrategyEvaluationResponse denseHybridStrategyResult(Long userId, EvaluationRun baselineRun) {
        try {
            EvaluationRun denseRun = evaluateWithHybridProvider(userId, REMOTE_DENSE_PROVIDER);
            return availableStrategyResult(
                    "dense-hybrid",
                    REMOTE_DENSE_PROVIDER,
                    hybridRetrievalStrategy.strategyName(),
                    hybridRetrievalStrategy.description(),
                    denseRun,
                    baselineRun
            );
        } catch (RuntimeException ex) {
            log.warn("Dense hybrid retrieval evaluation is unavailable. provider={}, reason={}",
                    REMOTE_DENSE_PROVIDER,
                    safeUnavailableReason(ex));
            return unavailableStrategyResult(
                    "dense-hybrid",
                    REMOTE_DENSE_PROVIDER,
                    hybridRetrievalStrategy.strategyName(),
                    hybridRetrievalStrategy.description(),
                    safeUnavailableReason(ex)
            );
        }
    }

    private RagRetrievalStrategyEvaluationResponse denseHybridRerankStrategyResult(Long userId, EvaluationRun baselineRun) {
        try {
            EvaluationRun denseRerankRun = evaluateDenseHybridWithRerank(userId);
            return availableStrategyResult(
                    "dense-hybrid-rerank",
                    REMOTE_DENSE_PROVIDER,
                    hybridRetrievalStrategy.strategyName() + "+remote-rerank",
                    hybridRetrievalStrategy.description() + " plus remote rerank",
                    denseRerankRun,
                    baselineRun
            );
        } catch (RuntimeException ex) {
            log.warn("Dense hybrid rerank retrieval evaluation is unavailable. embeddingProvider={}, rerankProvider={}, reason={}",
                    REMOTE_DENSE_PROVIDER,
                    REMOTE_RERANK_PROVIDER,
                    safeUnavailableReason(ex));
            return unavailableStrategyResult(
                    "dense-hybrid-rerank",
                    REMOTE_DENSE_PROVIDER,
                    hybridRetrievalStrategy.strategyName() + "+remote-rerank",
                    hybridRetrievalStrategy.description() + " plus remote rerank",
                    safeUnavailableReason(ex)
            );
        }
    }

    private RagRetrievalStrategyEvaluationResponse availableStrategyResult(String strategyKey,
                                                                          String embeddingProvider,
                                                                          String retrievalStrategy,
                                                                          String retrievalStrategyDescription,
                                                                          EvaluationRun run,
                                                                          EvaluationRun baselineRun) {
        return new RagRetrievalStrategyEvaluationResponse(
                strategyKey,
                embeddingProvider,
                retrievalStrategy,
                retrievalStrategyDescription,
                STATUS_AVAILABLE,
                null,
                run.passedCaseCount(),
                run.passRate(),
                run.positiveCaseCount(),
                run.hitAtK(),
                run.mrr(),
                roundToFourDecimals(run.hitAtK() - baselineRun.hitAtK()),
                roundToFourDecimals(run.mrr() - baselineRun.mrr()),
                run.caseResponses()
        );
    }

    private RagRetrievalStrategyEvaluationResponse unavailableStrategyResult(String strategyKey,
                                                                            String embeddingProvider,
                                                                            String retrievalStrategy,
                                                                            String retrievalStrategyDescription,
                                                                            String unavailableReason) {
        return new RagRetrievalStrategyEvaluationResponse(
                strategyKey,
                embeddingProvider,
                retrievalStrategy,
                retrievalStrategyDescription,
                STATUS_UNAVAILABLE,
                unavailableReason,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of()
        );
    }

    private String safeUnavailableReason(RuntimeException ex) {
        return ex.getMessage() == null || ex.getMessage().isBlank()
                ? "dense hybrid retrieval is unavailable"
                : ex.getMessage();
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
                                                                    CaseRetriever retriever) {
        List<String> queryKeywords = retrievalKeywordService.resolveKeywords(caseDefinition.question());
        List<ChunkSearchResponse> chunks = retriever.retrieve(userId, queryKeywords, RETRIEVAL_EVALUATION_LIMIT);
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

    @FunctionalInterface
    private interface CaseRetriever {

        List<ChunkSearchResponse> retrieve(Long userId, List<String> keywords, Integer limit);
    }
}
