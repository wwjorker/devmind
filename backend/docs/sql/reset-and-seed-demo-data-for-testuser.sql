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
        'AI 问答系统不能只看有没有回答，还要判断回答是否基于正确上下文、是否召回了相关 chunk、是否覆盖了期望知识点。\n\n',
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
