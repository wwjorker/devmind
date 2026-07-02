-- DevMind demo data reset script.
-- Scope: only the local demo user `testuser`.
-- Run this in DBeaver before a formal demo if your local data contains too many historical test records.
-- It does not delete the user account, and it does not touch other databases or other users.

USE devmind;

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

SET @demo_username := _utf8mb4'testuser' COLLATE utf8mb4_unicode_ci;
SET @demo_user_id := (
    SELECT id
    FROM user_account
    WHERE username COLLATE utf8mb4_unicode_ci = @demo_username
    LIMIT 1
);

-- Stop here mentally if this returns NULL. Register/login testuser in the app first, then rerun this script.
SELECT @demo_user_id AS demo_user_id;

DELETE f
FROM ai_ask_feedback f
WHERE f.user_id = @demo_user_id;

DELETE l
FROM ai_ask_log l
WHERE l.user_id = @demo_user_id;

DELETE v
FROM knowledge_document_chunk_vector v
WHERE v.user_id = @demo_user_id;

DELETE c
FROM knowledge_document_chunk c
WHERE c.user_id = @demo_user_id;

DELETE d
FROM knowledge_document d
WHERE d.user_id = @demo_user_id;

-- 1. Redis cache penetration
INSERT INTO knowledge_document (user_id, title, content, source_type, tags, summary, status)
SELECT
    @demo_user_id,
    'Redis 缓存穿透复盘',
    CONCAT(
        '# Redis 缓存穿透复盘\n\n',
        '## 问题\n\n',
        '当大量请求访问一个不存在的 key 时，请求可能反复绕过 Redis，直接打到 MySQL。\n\n',
        '## 根因\n\n',
        '系统只缓存真实存在的数据，不存在的数据没有写入缓存，所以每次请求都会成为缓存未命中。\n\n',
        '## 解决方案\n\n',
        '- 对不存在的数据缓存空值，并设置较短 TTL。\n',
        '- 在入口处校验非法参数，减少明显无效请求。\n',
        '- 对异常流量增加限流。\n',
        '- 对高风险查询场景使用布隆过滤器提前拦截。\n',
        '- 监控缓存 miss 率和数据库压力，及时发现异常访问。\n\n',
        '缓存穿透和缓存击穿、缓存雪崩不同。缓存穿透的核心是大量请求查询不存在的数据，导致缓存层无法拦截，数据库承受重复 miss 压力。'
    ),
    'bug_review',
    'Redis,缓存穿透,空值缓存,布隆过滤器,限流',
    '用于演示 Redis 缓存穿透的召回、引用和面试回答。',
    1
WHERE @demo_user_id IS NOT NULL;
SET @redis_doc_id := LAST_INSERT_ID();

INSERT INTO knowledge_document_chunk (document_id, user_id, chunk_index, content, token_count, status)
SELECT
    @redis_doc_id,
    @demo_user_id,
    0,
    'Redis 缓存穿透是大量请求查询不存在的数据，导致缓存无法命中并反复打到 MySQL。解决方案包括缓存空值并设置较短 TTL、入口参数校验、接口限流、使用布隆过滤器提前拦截高风险查询，并监控缓存 miss 率和数据库压力。它和缓存击穿、缓存雪崩不同，目标是保护数据库免受重复 miss 冲击。',
    120,
    1
WHERE @demo_user_id IS NOT NULL;

-- 2. JWT logout Redis blacklist
INSERT INTO knowledge_document (user_id, title, content, source_type, tags, summary, status)
SELECT
    @demo_user_id,
    'JWT 退出登录与 Redis 黑名单',
    CONCAT(
        '# JWT 退出登录与 Redis 黑名单\n\n',
        'JWT 是无状态 token。服务端默认不保存会话，所以用户点击退出登录后，如果不做额外处理，旧 token 在过期前仍然可能继续访问接口。\n\n',
        'DevMind 在退出登录时会解析当前 token 的剩余有效期，把 token 写入 Redis blacklist，并设置 TTL 为 token 的剩余过期时间。\n\n',
        '认证过滤器在校验 JWT 时，除了验证签名和过期时间，还会查询 Redis blacklist。如果 token 已经在黑名单中，就拒绝本次请求。'
    ),
    'project_doc',
    'JWT,Redis,blacklist,logout,认证',
    '用于演示 JWT 退出登录为什么需要 Redis blacklist。',
    1
WHERE @demo_user_id IS NOT NULL;
SET @jwt_doc_id := LAST_INSERT_ID();

INSERT INTO knowledge_document_chunk (document_id, user_id, chunk_index, content, token_count, status)
SELECT
    @jwt_doc_id,
    @demo_user_id,
    0,
    'JWT 是无状态 token。退出登录后，如果不做处理，未过期 token 仍可能继续访问接口。DevMind 把退出 token 写入 Redis blacklist，并设置 TTL 为 token 剩余有效期。认证过滤器每次校验 JWT 时都会检查 blacklist，如果 token 已退出就拒绝访问。',
    110,
    1
WHERE @demo_user_id IS NOT NULL;

-- 3. Flyway migration
INSERT INTO knowledge_document (user_id, title, content, source_type, tags, summary, status)
SELECT
    @demo_user_id,
    'Flyway migration 数据库迁移',
    CONCAT(
        '# Flyway migration 数据库迁移\n\n',
        '手动在 DBeaver 执行 SQL 适合早期开发，但长期维护时容易出现不同环境表结构不一致。\n\n',
        'DevMind 使用 Flyway migration 管理数据库结构变更。迁移脚本放在 src/main/resources/db/migration，命名为 V版本号__说明.sql。\n\n',
        '应用启动时，Flyway 会检查数据库当前版本，自动执行尚未执行过的迁移脚本。'
    ),
    'db_note',
    'Flyway,migration,database,MySQL,数据库',
    '用于演示 Flyway 解决数据库结构版本管理问题。',
    1
WHERE @demo_user_id IS NOT NULL;
SET @flyway_doc_id := LAST_INSERT_ID();

INSERT INTO knowledge_document_chunk (document_id, user_id, chunk_index, content, token_count, status)
SELECT
    @flyway_doc_id,
    @demo_user_id,
    0,
    'Flyway migration 用版本化 SQL 脚本管理数据库结构变更。应用启动时会检查数据库版本并自动执行未执行过的迁移，避免不同环境手动执行 SQL 导致结构漂移。它让数据库结构和代码一起进入 Git 版本管理。',
    95,
    1
WHERE @demo_user_id IS NOT NULL;

-- 4. LLM Provider abstraction
INSERT INTO knowledge_document (user_id, title, content, source_type, tags, summary, status)
SELECT
    @demo_user_id,
    'LlmClient 与 LLM Provider 抽象',
    CONCAT(
        '# LlmClient 与 LLM Provider 抽象\n\n',
        '如果在 AiAskService 里直接写 DeepSeek HTTP 调用，业务流程会和某一个模型供应商强绑定。\n\n',
        'DevMind 抽象了 LlmClient 接口，并通过 LlmClientRouter 选择具体 Provider：MockLlmClient 用于本地开发和测试，DeepSeekLlmClient 用于真实模型调用。\n\n',
        '这样可以支持 Mock/DeepSeek 切换，方便本地测试、控制成本，也避免 RAG 主链路和单一模型厂商强耦合。'
    ),
    'project_doc',
    'LlmClient,DeepSeek,Mock,Provider,RAG',
    '用于演示为什么要抽象 LLM Provider。',
    1
WHERE @demo_user_id IS NOT NULL;
SET @llm_doc_id := LAST_INSERT_ID();

INSERT INTO knowledge_document_chunk (document_id, user_id, chunk_index, content, token_count, status)
SELECT
    @llm_doc_id,
    @demo_user_id,
    0,
    'DevMind 使用 LlmClient 接口和 LlmClientRouter 隔离模型供应商。MockLlmClient 用于本地开发测试，DeepSeekLlmClient 用于真实模型调用。这样业务服务不直接依赖某个厂商 API，方便切换模型、控制成本，并已支持真实模型失败后的 provider fallback。',
    110,
    1
WHERE @demo_user_id IS NOT NULL;

-- 5. No-context fallback
INSERT INTO knowledge_document (user_id, title, content, source_type, tags, summary, status)
SELECT
    @demo_user_id,
    'RAG 无上下文兜底',
    CONCAT(
        '# RAG 无上下文兜底\n\n',
        'RAG 系统不是只要能生成答案就算成功。如果知识库里没有相关资料，模型仍然强行回答，就容易出现幻觉。\n\n',
        'DevMind 在调用模型前会先检索相关 chunk。如果没有检索到有效 chunk，系统会返回无上下文兜底提示，而不是强行构造 Prompt 调用模型。\n\n',
        '这样可以减少模型幻觉、避免无效 token 成本，并提示用户补充知识库材料。'
    ),
    'java_note',
    'RAG,fallback,retrieval,chunks,幻觉控制',
    '用于演示检索不到 chunk 时的无上下文兜底策略。',
    1
WHERE @demo_user_id IS NOT NULL;
SET @fallback_doc_id := LAST_INSERT_ID();

INSERT INTO knowledge_document_chunk (document_id, user_id, chunk_index, content, token_count, status)
SELECT
    @fallback_doc_id,
    @demo_user_id,
    0,
    '检索不到 chunk 时，DevMind 不会强行让模型编答案，而是返回知识库资料不足的无上下文兜底提示。这样可以减少幻觉，避免无效 token 成本，并提醒用户补充相关笔记。',
    80,
    1
WHERE @demo_user_id IS NOT NULL;

-- 6. RAG evaluation
INSERT INTO knowledge_document (user_id, title, content, source_type, tags, summary, status)
SELECT
    @demo_user_id,
    'RAG 回答质量评估',
    CONCAT(
        '# RAG 回答质量评估\n\n',
        'AI 问答系统不能只看有没有回答，还要判断回答是否基于正确上下文、是否召回了人工标注的相关文档。\n\n',
        'DevMind 通过 ai_ask_log 记录问答日志，通过 ai_ask_feedback 记录 helpful 或 bad case，通过 RAG evaluation dataset 保存标准问题、期望关键词和期望答案。\n\n',
        '当前版本会展示覆盖率、Hit@3、MRR 和 bad case 反馈，用来判断召回结果是否命中并排在前面。'
    ),
    'interview_review',
    'RAG,evaluation,bad case,hit rate,MRR',
    '用于演示如何判断 RAG 回答质量。',
    1
WHERE @demo_user_id IS NOT NULL;
SET @evaluation_doc_id := LAST_INSERT_ID();

INSERT INTO knowledge_document_chunk (document_id, user_id, chunk_index, content, token_count, status)
SELECT
    @evaluation_doc_id,
    @demo_user_id,
    0,
    '判断 RAG 回答质量不能只看模型有没有输出。DevMind 使用 ai_ask_log 记录召回 chunks、Prompt、token 和耗时，使用 bad case feedback 记录问题原因和期望答案，并通过 RAG evaluation dataset 检查标准问题覆盖率、Hit@3 和 MRR。',
    115,
    1
WHERE @demo_user_id IS NOT NULL;

-- 7. MySQL index optimization
INSERT INTO knowledge_document (user_id, title, content, source_type, tags, summary, status)
SELECT
    @demo_user_id,
    'MySQL 索引优化',
    CONCAT(
        '# MySQL 索引优化\n\n',
        'MySQL 索引用来减少扫描行数。设计索引时要结合 where、order by、join 和数据区分度，优先让高频查询走合适的联合索引。\n\n',
        '常见原则包括最左前缀、覆盖索引、避免在索引列上使用函数、避免低选择性字段单独建索引。执行计划中 type、key、rows、Extra 可以帮助判断是否走到了预期索引。\n\n',
        '索引不是越多越好，过多索引会增加写入和维护成本，业务查询变化后也需要定期复盘索引收益。'
    ),
    'db_note',
    'MySQL,index,索引优化,联合索引,explain',
    '用于演示 MySQL 索引设计、覆盖索引和执行计划分析。',
    1
WHERE @demo_user_id IS NOT NULL;
SET @mysql_index_doc_id := LAST_INSERT_ID();

INSERT INTO knowledge_document_chunk (document_id, user_id, chunk_index, content, token_count, status)
SELECT
    @mysql_index_doc_id,
    @demo_user_id,
    0,
    'MySQL 索引优化的核心是减少扫描行数并稳定查询路径。常用做法包括按高频查询设计联合索引、遵守最左前缀、尽量使用覆盖索引、避免在索引列上做函数计算，并通过 explain 查看 type、key、rows 和 Extra。索引过多会增加写入成本，需要结合业务查询收益取舍。',
    115,
    1
WHERE @demo_user_id IS NOT NULL;

-- 8. MySQL transaction isolation
INSERT INTO knowledge_document (user_id, title, content, source_type, tags, summary, status)
SELECT
    @demo_user_id,
    'MySQL 事务隔离级别',
    CONCAT(
        '# MySQL 事务隔离级别\n\n',
        'MySQL 事务隔离级别用于在并发读写之间平衡一致性和性能，常见级别包括 Read Uncommitted、Read Committed、Repeatable Read 和 Serializable。\n\n',
        '在 InnoDB 中，默认 Repeatable Read 通过 MVCC 保证同一事务内多次一致读结果稳定，并通过 next-key lock 等机制减少幻读问题。\n\n',
        '面试中要能区分脏读、不可重复读、幻读，以及快照读和当前读的行为差异。'
    ),
    'db_note',
    'MySQL,transaction,isolation,MVCC,Repeatable Read',
    '用于演示 MySQL 事务隔离、MVCC 和并发一致性问题。',
    1
WHERE @demo_user_id IS NOT NULL;
SET @mysql_tx_doc_id := LAST_INSERT_ID();

INSERT INTO knowledge_document_chunk (document_id, user_id, chunk_index, content, token_count, status)
SELECT
    @mysql_tx_doc_id,
    @demo_user_id,
    0,
    'MySQL 事务隔离级别包括 Read Uncommitted、Read Committed、Repeatable Read 和 Serializable。InnoDB 默认 Repeatable Read，依赖 MVCC 提供一致性快照读，并通过 next-key lock 等机制减少幻读。理解脏读、不可重复读、幻读、快照读和当前读，是分析并发事务问题的基础。',
    120,
    1
WHERE @demo_user_id IS NOT NULL;

-- 9. Java thread pool parameters
INSERT INTO knowledge_document (user_id, title, content, source_type, tags, summary, status)
SELECT
    @demo_user_id,
    '线程池核心参数与拒绝策略',
    CONCAT(
        '# 线程池核心参数与拒绝策略\n\n',
        'Java 线程池通过 corePoolSize、maximumPoolSize、keepAliveTime、workQueue、threadFactory 和 rejectedExecutionHandler 控制任务执行。\n\n',
        '任务提交后先使用核心线程，再进入队列，队列满后才扩容到最大线程数，继续满载时触发拒绝策略。\n\n',
        '拒绝策略常见有 AbortPolicy、CallerRunsPolicy、DiscardPolicy 和 DiscardOldestPolicy。实际项目要结合任务类型、队列容量和降级策略设置参数。'
    ),
    'java_note',
    'Java,线程池,ThreadPoolExecutor,拒绝策略,并发',
    '用于演示线程池参数、任务队列和拒绝策略。',
    1
WHERE @demo_user_id IS NOT NULL;
SET @thread_pool_doc_id := LAST_INSERT_ID();

INSERT INTO knowledge_document_chunk (document_id, user_id, chunk_index, content, token_count, status)
SELECT
    @thread_pool_doc_id,
    @demo_user_id,
    0,
    '线程池核心参数包括 corePoolSize、maximumPoolSize、keepAliveTime、workQueue、threadFactory 和 rejectedExecutionHandler。任务会优先使用核心线程，队列满后扩容到最大线程数，再满则触发拒绝策略。常见拒绝策略包括 AbortPolicy、CallerRunsPolicy、DiscardPolicy 和 DiscardOldestPolicy。',
    110,
    1
WHERE @demo_user_id IS NOT NULL;

-- 10. Spring Bean lifecycle
INSERT INTO knowledge_document (user_id, title, content, source_type, tags, summary, status)
SELECT
    @demo_user_id,
    'Spring Bean 生命周期与 IoC',
    CONCAT(
        '# Spring Bean 生命周期与 IoC\n\n',
        'Spring IoC 容器负责 Bean 的创建、依赖注入、初始化和销毁。常见流程包括实例化、属性填充、Aware 回调、BeanPostProcessor 前后置处理、初始化方法和销毁方法。\n\n',
        'BeanPostProcessor 是框架扩展点，AOP 代理等能力常在初始化前后织入。理解生命周期有助于排查依赖注入、代理失效和初始化顺序问题。\n\n',
        'IoC 的核心价值是把对象创建和依赖管理交给容器，让业务代码面向接口和组件协作。'
    ),
    'java_note',
    'Spring,Bean,IoC,生命周期,BeanPostProcessor',
    '用于演示 Spring Bean 生命周期、IoC 和扩展点。',
    1
WHERE @demo_user_id IS NOT NULL;
SET @spring_bean_doc_id := LAST_INSERT_ID();

INSERT INTO knowledge_document_chunk (document_id, user_id, chunk_index, content, token_count, status)
SELECT
    @spring_bean_doc_id,
    @demo_user_id,
    0,
    'Spring Bean 生命周期包括实例化、属性填充、Aware 回调、BeanPostProcessor 前后置处理、初始化方法和销毁方法。IoC 容器负责对象创建和依赖管理，BeanPostProcessor 是 AOP 代理和框架扩展的重要入口。理解这些流程有助于排查代理失效和初始化顺序问题。',
    115,
    1
WHERE @demo_user_id IS NOT NULL;

-- 11. MySQL slow query explain
INSERT INTO knowledge_document (user_id, title, content, source_type, tags, summary, status)
SELECT
    @demo_user_id,
    'MySQL 慢查询与 explain',
    CONCAT(
        '# MySQL 慢查询与 explain\n\n',
        '慢查询排查通常从慢查询日志、SQL 频率、数据量和执行计划入手。explain 可以展示访问类型、使用索引、扫描行数和额外信息。\n\n',
        '重点关注 type 是否退化为 ALL、key 是否为空、rows 是否过大、Extra 是否出现 Using filesort 或 Using temporary。\n\n',
        '优化思路包括补充合适索引、改写 SQL、减少返回列、分页优化和避免不必要的排序或临时表。'
    ),
    'db_note',
    'MySQL,slow query,explain,SQL优化,索引',
    '用于演示 MySQL 慢查询排查和 explain 执行计划分析。',
    1
WHERE @demo_user_id IS NOT NULL;
SET @mysql_slow_doc_id := LAST_INSERT_ID();

INSERT INTO knowledge_document_chunk (document_id, user_id, chunk_index, content, token_count, status)
SELECT
    @mysql_slow_doc_id,
    @demo_user_id,
    0,
    'MySQL 慢查询排查可以从慢查询日志和 explain 执行计划开始。重点看 type、key、rows、Extra 等字段，识别全表扫描、未命中索引、Using filesort、Using temporary 等问题。优化方向包括补索引、改写 SQL、减少返回列、优化分页和避免不必要排序。',
    115,
    1
WHERE @demo_user_id IS NOT NULL;

-- 12. Redis persistence RDB/AOF
INSERT INTO knowledge_document (user_id, title, content, source_type, tags, summary, status)
SELECT
    @demo_user_id,
    'Redis 持久化 RDB/AOF',
    CONCAT(
        '# Redis 持久化 RDB/AOF\n\n',
        'Redis 持久化主要有 RDB 和 AOF。RDB 是某个时间点的数据快照，恢复快但可能丢失最近一次快照后的数据。\n\n',
        'AOF 记录写命令日志，数据安全性更高，但文件可能更大，需要 rewrite 压缩。实际生产会根据恢复速度、数据丢失窗口和写入开销选择方案。\n\n',
        '这个主题和缓存穿透同属 Redis，但关注点是数据恢复和持久化可靠性，不是缓存 miss。'
    ),
    'redis_note',
    'Redis,RDB,AOF,持久化,rewrite',
    '用于演示 Redis RDB/AOF 持久化机制和取舍。',
    1
WHERE @demo_user_id IS NOT NULL;
SET @redis_persistence_doc_id := LAST_INSERT_ID();

INSERT INTO knowledge_document_chunk (document_id, user_id, chunk_index, content, token_count, status)
SELECT
    @redis_persistence_doc_id,
    @demo_user_id,
    0,
    'Redis 持久化包括 RDB 和 AOF。RDB 是快照，恢复速度快但可能丢失最近数据；AOF 记录写命令，数据安全性更高但文件更大，需要 rewrite。选择方案时要权衡恢复速度、可接受的数据丢失窗口和写入开销。它关注数据恢复，不是缓存穿透。',
    115,
    1
WHERE @demo_user_id IS NOT NULL;

-- 13. Redis distributed lock
INSERT INTO knowledge_document (user_id, title, content, source_type, tags, summary, status)
SELECT
    @demo_user_id,
    'Redis 分布式锁',
    CONCAT(
        '# Redis 分布式锁\n\n',
        'Redis 分布式锁常用 SET key value NX PX timeout 实现，要求加锁具备互斥性、过期时间和唯一 value，释放锁时要校验 value 再删除，避免误删别人的锁。\n\n',
        '锁过期时间要覆盖业务执行时间，长任务需要续期。高可用场景还要考虑 Redis 主从切换、锁丢失和 Redlock 等方案的取舍。\n\n',
        '这个主题和缓存穿透同属 Redis，但关注并发互斥，不是缓存空值或 miss。'
    ),
    'redis_note',
    'Redis,分布式锁,SET NX PX,Redlock,并发',
    '用于演示 Redis 分布式锁、过期时间和安全释放。',
    1
WHERE @demo_user_id IS NOT NULL;
SET @redis_lock_doc_id := LAST_INSERT_ID();

INSERT INTO knowledge_document_chunk (document_id, user_id, chunk_index, content, token_count, status)
SELECT
    @redis_lock_doc_id,
    @demo_user_id,
    0,
    'Redis 分布式锁常用 SET key value NX PX timeout 实现。加锁要设置过期时间和唯一 value，释放锁时通过 Lua 脚本校验 value 后删除，避免误删其他客户端的锁。长任务需要考虑续期，高可用场景要评估主从切换和 Redlock 取舍。',
    115,
    1
WHERE @demo_user_id IS NOT NULL;

-- 14. HTTP and TCP basics
INSERT INTO knowledge_document (user_id, title, content, source_type, tags, summary, status)
SELECT
    @demo_user_id,
    'HTTP 与 TCP 基础',
    CONCAT(
        '# HTTP 与 TCP 基础\n\n',
        'TCP 是传输层协议，提供可靠、有序、面向连接的字节流，核心机制包括三次握手、四次挥手、重传、拥塞控制和流量控制。\n\n',
        'HTTP 是应用层协议，基于请求响应模型描述资源访问。HTTP/1.1 支持长连接，HTTP/2 支持多路复用，HTTPS 在 HTTP 与 TCP 之间加入 TLS。\n\n',
        '面试中要能区分连接建立、报文语义、状态码、幂等性和连接复用等概念。'
    ),
    'network_note',
    'HTTP,TCP,HTTPS,三次握手,长连接',
    '用于演示 HTTP 与 TCP 的分层关系和基础概念。',
    1
WHERE @demo_user_id IS NOT NULL;
SET @http_tcp_doc_id := LAST_INSERT_ID();

INSERT INTO knowledge_document_chunk (document_id, user_id, chunk_index, content, token_count, status)
SELECT
    @http_tcp_doc_id,
    @demo_user_id,
    0,
    'TCP 是传输层协议，负责可靠、有序、面向连接的字节流，涉及三次握手、四次挥手、重传、拥塞控制和流量控制。HTTP 是应用层协议，基于请求响应模型描述资源访问；HTTPS 则在 HTTP 与 TCP 之间加入 TLS。HTTP/1.1 长连接和 HTTP/2 多路复用是常见面试点。',
    120,
    1
WHERE @demo_user_id IS NOT NULL;

SELECT
    COUNT(*) AS demo_document_count
FROM knowledge_document
WHERE user_id = @demo_user_id
  AND status = 1;

SELECT
    COUNT(*) AS demo_chunk_count
FROM knowledge_document_chunk
WHERE user_id = @demo_user_id
  AND status = 1;
