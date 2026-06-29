# DevMind 面试讲解文档

## 1. 项目一句话介绍

DevMind 是一个面向开发学习和项目复盘的 AI 知识库系统。用户可以把 Java 八股、项目文档、Bug 复盘等内容录入或导入知识库，系统自动分块、检索相关片段、构造 Prompt、调用 DeepSeek 生成答案，并记录 token 用量、耗时、召回 chunk、引用来源和 bad case 反馈，用于后续质量分析。

更短的说法：

```text
这是一个把 RAG 问答能力嵌入到标准 Java 后端系统里的知识库项目。
```

## 2. 为什么做这个项目

这个项目不是为了做一个聊天接口，而是为了补足传统 CRUD 项目缺少的差异化。

它仍然是 Java 后端项目，有登录认证、数据库建模、接口设计、分层、日志、数据隔离、Redis、Flyway 和 CI；同时加入了 AI 应用链路，包括 RAG、Prompt 构造、LLM Provider 抽象、token 成本观测和 bad case 质量反馈。

面试时可以说：

```text
我不想只做一个套壳 AI Demo，所以把 AI 问答放进一个完整后端系统里做。项目里既有传统后端的认证、数据建模、接口设计和数据库迁移，也有 AI 应用里的检索、Prompt、模型调用、token 统计和 bad case 分析。
```

## 3. 核心业务流程

```text
注册/登录
-> 创建或导入知识文档
-> 自动生成 chunk
-> 用户提问
-> 解析中英文关键词
-> 检索相关 chunk
-> 结合内容、标题、标签、类型计算分数
-> 构造 Prompt
-> 通过 LlmClientRouter 调用 Mock 或 DeepSeek
-> 返回答案和 citations
-> 写入 ai_ask_log
-> 用户提交 feedback
-> evaluation summary / RAG 评估集汇总质量指标
```

## 4. 表设计怎么讲

主要有 5 张业务表：

- `user_account`：用户表。
- `knowledge_document`：知识文档表。
- `knowledge_document_chunk`：文档切片表。
- `ai_ask_log`：AI 问答日志表。
- `ai_ask_feedback`：AI 反馈表。

设计重点：

- 核心业务表都有 `user_id`，保证用户级数据隔离。
- 文档和 chunk 使用软归档，避免误删历史数据，也方便保留历史问答引用。
- `ai_ask_log` 和 `ai_ask_feedback` 分开，因为一次问答日志是客观调用记录，反馈是主观质量评价。
- 使用 Flyway 管理数据库迁移，避免依赖手动复制 SQL。

另外，当前 RAG evaluation dataset 是代码中的标准 case 集合，不是数据库表。它用标准问题、期望答案和人工标注的相关文档检查系统是否召回正确上下文，不只是看模型有没有生成文字。检索评估接口会输出 Hit@3、MRR 和首个相关片段排名；期望关键词只作为诊断展示，不作为相关性裁判。后续接入 embedding 或 rerank 时，可以用同一批 gold label case 做前后对比。

## 5. 为什么要切片

长文档不适合直接交给大模型。

原因：

- token 成本高。
- 模型上下文会被无关内容污染。
- 后续做 embedding、向量检索、rerank 时需要更细粒度的检索单元。

面试回答：

```text
切片的目的是把长文档变成可检索的最小知识单元，减少 token 浪费，也方便后续从关键词检索升级到向量检索和 rerank。
```

## 6. 当前检索策略怎么讲

当前版本不是简单的单关键词 LIKE，而是通过 `RetrievalStrategy` 抽象出来的混合检索 V1。

做了几件事：

- 支持中英文多关键词解析，而不是只取一个英文 token。
- 检索范围不只包括 chunk 内容，也包括文档标题、标签、来源类型等元数据。
- 计算召回分数时，内容匹配权重最高，标题、标签、类型分别加权。
- 保留 MySQL FULLTEXT 作为可解释的关键词/BM25 风格基线。
- 增加本地 embedding-style 相似度重排：通过 `EmbeddingClient` 把问题和 chunk 文本转成归一化稀疏向量，用余弦相似度作为额外排序信号。
- 对内容高度重复的 chunk 做降权，避免同一份重复笔记挤占所有引用位置。

为什么没有一开始直接上外部向量库：

- 能快速验证文档、chunk、检索、Prompt、LLM 调用是否跑通。
- 面试时能清楚解释 score、Hit@3、MRR 怎么来。
- 关键词检索对八股、技术术语、错误码、类名、接口名这类内容很有效。
- 先把检索策略和 embedding provider 抽象好，再把本地 embedding 实现替换成真实 embedding API 和向量库，风险更低。

可以这样回答：

```text
我没有一开始就把项目绑定到某个向量数据库，而是先抽象 RetrievalStrategy 和 EmbeddingClient。当前主策略是 hybrid retrieval：先用 MySQL FULLTEXT、多关键词和元数据召回做可解释基线，再用本地 embedding-style 相似度做重排。这样可以先用 gold label 的 Hit@3/MRR 跑出基线，后续替换成真实 embedding API 或向量库时，也能用同一套评估集对比效果。
```

## 7. 为什么要抽象 LlmClient

如果直接在 `AiAskService` 里写 DeepSeek HTTP 调用，业务编排和模型供应商会耦合。

现在的设计是：

```text
AiAskService -> LlmClientRouter -> LlmClient
```

已有实现：

- `MockLlmClient`：本地开发和测试用，不花钱、不依赖外部 API。
- `DeepSeekLlmClient`：真实模型调用。

好处：

- 本地开发默认 mock，稳定。
- 配置环境变量后切换 DeepSeek。
- 后续接其他模型时，不需要改 RAG 主流程。
- 测试时可以验证业务编排，而不依赖真实模型网络。

## 8. 为什么要记录 token usage

真实模型调用是有成本的。

`ai_ask_log` 记录：

- `prompt_tokens`
- `completion_tokens`
- `total_tokens`
- `elapsed_ms`
- `model_provider`
- `retrieved_chunk_ids`
- `prompt_preview`
- `status`

面试回答：

```text
我没有只关注能不能调通模型，还记录了 token usage、耗时和召回 chunk。这样后续可以分析成本、定位 bad case，也能判断 RAG 检索是否引入了过多无关上下文。
```

## 9. 为什么要做 bad case feedback

AI 项目不能只看“有回答”，还要看“回答质量”。

所以 DevMind 有：

```text
ai_ask_log
ai_ask_feedback
RAG evaluation dataset
evaluation summary
```

`ai_ask_feedback` 保存：

- helpful 是否有用。
- reason 为什么不好。
- expected_answer 期望答案。

RAG 评估集保存：

- 标准问题。
- 期望答案。
- 期望关键词。
- 该问题是否被最近一次问答覆盖。

这让项目具备质量闭环：

```text
模型回答 -> 用户反馈 -> 沉淀 bad case -> 标准问题评估 -> 优化 Prompt/检索
```

## 10. 为什么引入 Flyway

一开始手动在 DBeaver 执行 SQL 可以快速开发，但不适合长期维护。

引入 Flyway 后：

- 新环境只需要创建 `devmind` 数据库，启动项目自动建表。
- 后续每次表结构变更都用 `V版本号__说明.sql` 管理。
- 数据库结构变化有版本记录，和代码一起提交 Git。
- 更接近企业项目的数据库变更流程。

面试回答：

```text
我把手动 SQL 初始化升级成 Flyway 迁移管理。这样数据库结构和代码版本绑定，新环境启动时会自动执行迁移，后续加字段、建表也能通过版本化脚本追踪。
```

## 11. Redis 在项目里做什么

项目使用 Redis 做 JWT logout blacklist。

JWT 本身是无状态的。服务端默认不保存会话，所以用户退出登录后，如果不做额外处理，旧 token 在过期前仍然可能继续访问接口。

DevMind 的做法：

```text
退出登录
-> 解析 token 剩余有效期
-> 写入 Redis blacklist
-> TTL 设置为 token 剩余过期时间
-> 认证过滤器每次校验 token 时检查 blacklist
```

面试回答：

```text
JWT 的优点是无状态，但退出登录会有 token 未过期仍可用的问题。我用 Redis blacklist 保存退出 token，并设置和 token 剩余有效期一致的 TTL。这样不用改 JWT 的无状态设计，也能实现退出后拦截。
```

## 12. 简历怎么写

项目描述：

```text
基于 Spring Boot 和 Vue 3 设计并实现开发学习知识库系统，支持用户认证、知识文档管理、文件导入、自动分块、多关键词检索、RAG 问答、AI 调用日志、bad case 反馈和评估统计。
```

亮点 bullet：

- 设计用户级知识库数据模型，实现文档 CRUD、软归档、文件导入、自动 chunk 生成和更新重建机制。
- 实现 RAG 问答链路：问题解析、chunk 检索、Prompt 构造、LLM 调用、答案引用来源返回。
- 优化检索策略，支持中英文多关键词、标题/标签/类型元数据召回和重复 chunk 降权。
- 抽象 `LlmClient` 与 `LlmClientRouter`，支持 Mock 与 DeepSeek Provider 切换，降低模型调用和业务编排耦合。
- 基于 Redis blacklist 实现 JWT 退出登录拦截，并使用 TTL 控制黑名单生命周期。
- 记录模型 provider、prompt preview、召回 chunk、token usage、耗时和调用状态，支持 AI 调用成本观测与问题排查。
- 设计 bad case feedback、RAG evaluation dataset 与前端评估看板，支持 Hit@3/MRR 检索质量分析和迭代优化。
- 引入 Flyway 与 GitHub Actions，提升数据库迁移、测试和前端构建的工程化程度。

## 13. 面试可能追问

### Q1：这个项目和普通 CRUD 有什么区别？

CRUD 只是知识库入口，核心是后面的 RAG 链路和质量闭环：

```text
文档 -> chunk -> 检索 -> prompt -> LLM -> log -> feedback -> evaluation
```

### Q2：为什么不用一开始就做向量数据库？

第一版先用关键词检索跑通主链路，便于调试和解释。现在已经做了多关键词、元数据召回和重复 chunk 降权。后续会升级为关键词 + 向量的混合检索，再加 rerank。

### Q3：怎么防止模型胡说？

当前做了四件事：

- Prompt 要求基于检索上下文回答。
- 检索不到上下文时走 no-context fallback，不强行编答案。
- 返回 citations，让用户知道依据来自哪个 chunk。
- 用 feedback 和 evaluation case 记录错误回答，后续优化检索和 Prompt。

### Q4：如果 DeepSeek 挂了怎么办？

当前通过 `LlmClient` 抽象隔离 provider。真实模型调用失败时，系统会先记录失败日志，再降级到本地 Mock Provider 返回兜底回答，避免接口直接 500。后续还可以继续补超时配置、限流和重试。

### Q5：为什么需要前端？

Swagger 和 HTTP 文件适合开发调试，但面试展示时不直观。前端把知识文档、AI 回答、引用来源、token 用量、Prompt Preview、评估集和问答日志放在一个页面里，方便面试官快速理解系统闭环。

## 14. 后续规划

短期优先级：稳定成可投递版本。

1. 统一本地 JDK 17，保证 IDEA、本地命令行和 GitHub Actions 一致。
2. 清理演示数据，准备一套中文演示脚本和截图。
3. 用中文标准问题跑完 RAG 评估集，形成可展示的覆盖率、Hit@3 和 MRR。
4. 请 Claude Code 做一次阶段性代码审查，重点检查安全、异常、数据库设计、README 与实际功能是否一致。

中期优先级：提升检索含金量。

1. 增加 BM25 或全文检索。
2. 接入 embedding 和向量数据库。
3. 做关键词 + 向量混合检索。
4. 增加 rerank。
5. 基于当前 Hit@3/MRR 基线，对比 embedding、混合检索和 rerank 的提升。

长期优先级：补高级 AI 应用能力。

1. PDF/Word 文档解析。
2. SSE 流式输出。
3. 接口限流、超时配置和重试。
4. 轻量 Tool Calling。
