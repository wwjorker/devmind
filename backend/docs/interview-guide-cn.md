# DevMind 面试讲解文档

## 1. 项目一句话介绍

DevMind 是一个基于 Spring Boot 的个人开发知识库与 RAG 问答系统。用户可以把 Java 八股、项目文档、Bug 复盘等内容录入知识库，系统自动切片、检索相关片段、构造 Prompt、调用 DeepSeek 生成答案，并记录 token 用量、耗时、召回 chunk 和 bad case 反馈，用于后续质量分析。

更短的说法：

```text
这是一个把 AI 问答能力嵌入到标准 Java 后端系统里的个人知识库项目。
```

## 2. 为什么做这个项目

这个项目不是为了做一个聊天接口，而是为了补足传统 CRUD 项目缺少的差异化。

它仍然是 Java 后端项目，有登录、数据库、接口、分层、日志和数据隔离；同时加入了 AI 应用链路，包括 RAG、Prompt 构造、LLM Provider 抽象、token 成本观测和 bad case 质量反馈。

面试时可以说：

```text
我不想只做一个套壳 AI Demo，所以把 AI 问答放进一个完整后端系统里做。项目里既有传统后端的认证、数据建模、接口设计，也有 AI 应用里的检索、Prompt、模型调用、token 统计和 bad case 分析。
```

## 3. 核心业务流程

```text
注册/登录
-> 创建知识文档
-> 自动生成 chunk
-> 用户提问
-> 检索相关 chunk
-> 构造 Prompt
-> 通过 LlmClientRouter 调用 Mock 或 DeepSeek
-> 返回答案和 citations
-> 写入 ai_ask_log
-> 用户提交 feedback
-> evaluation summary 汇总 bad case 指标
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
- 文档和 chunk 使用软归档，避免误删历史数据。
- `ai_ask_log` 和 `ai_ask_feedback` 分开，因为一次问答日志是客观调用记录，反馈是主观质量评价。
- 使用 Flyway 管理数据库迁移，避免依赖手动复制 SQL。

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

## 6. 为什么现在先做关键词检索

当前版本是 keyword retrieval v0。

原因不是“不知道向量检索”，而是第一版先做可解释、可调试的检索链路：

- 能快速验证文档、chunk、检索、Prompt、LLM 调用是否跑通。
- 面试时能清楚解释 score 怎么来。
- 后续可以平滑升级为 hybrid retrieval：关键词 + 向量。

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

## 8. 为什么要记录 token usage

真实模型调用是有成本的。

`ai_ask_log` 记录：

- `prompt_tokens`
- `completion_tokens`
- `total_tokens`
- `elapsed_ms`
- `model_provider`
- `retrieved_chunk_ids`

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
evaluation summary
```

`ai_ask_feedback` 保存：

- helpful 是否有用。
- reason 为什么不好。
- expected_answer 期望答案。

`evaluation summary` 汇总：

- 总反馈数。
- helpful 数。
- bad case 数。
- bad case rate。
- 最近 bad case。

这让项目具备质量闭环：

```text
模型回答 -> 用户反馈 -> 沉淀 bad case -> 分析统计 -> 优化 Prompt/检索
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

## 11. 简历怎么写

项目描述：

```text
基于 Spring Boot 设计并实现个人开发知识库系统，支持用户认证、知识文档管理、自动切片、关键词检索、RAG 问答、AI 调用日志、bad case 反馈和评估统计。
```

亮点 bullet：

- 设计用户级知识库数据模型，实现文档 CRUD、软归档、自动 chunk 生成和更新重建机制。
- 实现 RAG 问答链路：问题解析、chunk 检索、Prompt 构造、LLM 调用、答案引用来源返回。
- 抽象 `LlmClient` 与 `LlmClientRouter`，支持 Mock 与 DeepSeek Provider 切换，降低模型调用和业务编排耦合。
- 记录模型 provider、prompt preview、召回 chunk、token usage 和耗时，支持 AI 调用成本观测与问题排查。
- 设计 `ai_ask_feedback` 与 evaluation summary，支持 bad case 标注、期望答案记录和 RAG 质量统计。
- 引入 Flyway 管理数据库迁移，提升项目初始化和表结构变更的工程化程度。

## 12. 面试可能追问

### Q1：这个项目和普通 CRUD 有什么区别？

CRUD 只是知识库入口，核心是后面的 RAG 链路和质量闭环：

```text
文档 -> chunk -> 检索 -> prompt -> LLM -> log -> feedback -> evaluation
```

### Q2：为什么不用一开始就做向量数据库？

第一版先用关键词检索跑通主链路，便于调试和解释。后续会升级为关键词 + 向量的混合检索，再加 rerank。

### Q3：怎么防止模型胡说？

当前做了三件事：

- Prompt 要求基于检索上下文回答。
- 返回 citations，让用户知道依据来自哪个 chunk。
- 用 feedback 和 bad case 记录错误回答，后续优化检索和 Prompt。

### Q4：如果 DeepSeek 挂了怎么办？

当前通过 `LlmClient` 抽象隔离 provider。后续可以加 provider fallback、超时配置、失败日志、限流和重试。

## 13. 后续规划

短期优先级：

1. 接入 embedding 和向量检索。
2. 做关键词 + 向量混合检索。
3. 增加 rerank。
4. 基于 bad case 做离线评估。
5. 增加轻量前端展示核心链路。
