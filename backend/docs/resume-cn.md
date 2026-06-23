# DevMind 简历项目描述

## 简历标题

```text
DevMind：个人开发知识库与 RAG 问答系统
```

## 技术栈

```text
Java 17、Spring Boot、Spring Security、JWT、MyBatis-Plus、MySQL、Flyway、Maven、Springdoc OpenAPI、DeepSeek API
```

## 一句话项目介绍

```text
基于 Spring Boot 设计并实现个人开发知识库系统，支持知识文档管理、自动切片、关键词检索、RAG 问答、DeepSeek 模型调用、AI 调用日志、token 成本观测和 bad case 评估统计。
```

## 简历 Bullet 版本

- 设计用户级知识库数据模型，实现知识文档 CRUD、软归档、自动 chunk 生成与文档更新后的 chunk 重建机制。
- 实现 RAG 问答链路，包括问题解析、chunk 检索、Prompt 构造、LLM 调用、答案返回与 citations 引用来源追踪。
- 抽象 `LlmClient` 与 `LlmClientRouter`，支持 Mock 与 DeepSeek Provider 切换，降低模型调用与业务编排耦合。
- 设计 `ai_ask_log` 问答日志，记录 provider、prompt preview、召回 chunk、token usage、耗时等信息，用于成本观测和问题排查。
- 设计 `ai_ask_feedback` 与 evaluation summary 接口，支持 helpful/bad case 标注、期望答案记录、bad case rate 统计和最近 bad case 分析。
- 引入 Flyway 管理数据库结构版本，降低本地初始化和后续表结构变更的手动 SQL 执行成本。

## 更短版本

- 基于 Spring Boot 实现个人开发知识库与 RAG 问答系统，支持文档管理、自动切片、关键词检索、Prompt 构造和 DeepSeek 模型调用。
- 抽象 LLM Provider 层，支持 Mock/DeepSeek 切换，并记录 token usage、耗时、召回 chunk 和模型来源，实现 AI 调用可观测。
- 设计 bad case feedback 与 evaluation summary，支持回答质量反馈、期望答案沉淀和 RAG 效果统计分析。

## 面试主线

面试时不要从“我调了 DeepSeek API”开始讲，而是按这条线讲：

```text
我做的是一个 Java 后端知识库系统，AI 问答只是其中一条业务链路。

用户先录入知识文档，系统自动切片；
提问时先检索相关 chunk，再构造 Prompt；
模型回答后返回引用来源；
同时记录 provider、token、耗时和召回 chunk；
如果回答不好，用户可以提交 bad case feedback；
最后 evaluation summary 会统计 bad case 占比和最近 bad case。
```

## 项目亮点解释

### 1. 不是简单 AI 套壳

项目有完整后端结构：

```text
认证 -> 文档管理 -> chunk 管理 -> 检索 -> AI 问答 -> 日志 -> 反馈 -> 评估
```

AI 只是系统能力的一部分，而不是单独调用一个聊天接口。

### 2. 有工程化分层

模型调用不直接写在业务 Service 里，而是：

```text
AiAskService -> LlmClientRouter -> LlmClient
```

这样后续接入其他模型 Provider 时，不需要改 RAG 主流程。

### 3. 有可观测性

问答日志记录：

```text
prompt preview
model provider
retrieved chunk ids
elapsed ms
prompt tokens
completion tokens
total tokens
```

这能用于排查回答质量问题，也能分析模型调用成本。

### 4. 有质量反馈闭环

反馈表记录：

```text
helpful
reason
expected answer
```

统计接口输出：

```text
total feedback count
bad case count
bad case rate
recent bad cases
```

这说明项目考虑了后续 RAG 迭代，而不是只做一次性 demo。

## 可继续优化点

- 接入 embedding 和向量数据库。
- 实现关键词 + 向量混合检索。
- 增加 rerank。
- 基于 bad case 构建离线评估集。
- 增加轻量前端展示核心链路。
