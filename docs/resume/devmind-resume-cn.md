# DevMind 简历写法

这份文档用于把 DevMind 写进中文简历。你可以根据简历篇幅选择完整版或精简版。

## 项目名称

DevMind：AI 开发者知识库与 RAG 问答系统

## 技术栈

Java 17、Spring Boot 3、Spring Security、MyBatis-Plus、MySQL、Redis、Flyway、JWT、Vue 3、Vite、TypeScript、DeepSeek API、GitHub Actions

## 项目描述

DevMind 是一个面向个人开发学习和项目复盘的 AI 知识库系统，支持用户维护技术笔记，自动生成文档分块，并基于检索上下文调用大模型生成带引用的回答。项目重点实现了认证鉴权、用户数据隔离、RAG 问答链路、LLM Provider 抽象、AI 调用日志、Token 统计、失败兜底、Bad Case 反馈和评估看板。

## 简历项目经历：完整版

- 基于 Spring Boot 3 和 Spring Security 实现 JWT 登录认证、BCrypt 密码加密和用户级数据隔离，保证知识文档、分块、AI 提问日志和反馈记录只能由所属用户访问。
- 设计知识文档与分块模型，在文档创建和更新后自动重建 chunk，为后续 RAG 检索和引用提供稳定的数据基础。
- 实现 RAG 问答链路：关键词提取、chunk 检索、可解释打分、Prompt 构建、LLM Provider 路由、答案生成和引用返回。
- 抽象 `LlmClient` 接口，接入 Mock 与 DeepSeek Provider，支持本地无 API Key 调试和真实模型调用切换，降低开发和测试成本。
- 建立 AI Ask Log 可观测链路，记录问题、检索关键词、Prompt Preview、Provider、耗时、Token 用量、召回 chunk ids 和成功/失败状态，支持前端恢复历史问答详情。
- 设计无上下文兜底策略，当知识库没有相关内容时不调用模型，直接返回信息不足提示，降低幻觉和无效 Token 消耗。
- 引入 Redis 实现 JWT Logout 黑名单，退出登录后将 Token 按剩余有效期写入 Redis，认证过滤器拦截已失效 Token。
- 使用 Flyway 管理数据库迁移，并配置 GitHub Actions 对后端测试和前端构建进行 CI 校验。

## 简历项目经历：精简版

- 基于 Spring Boot 3 + Vue 3 搭建 AI 知识库系统，实现 JWT 认证、用户数据隔离、知识文档 CRUD、自动分块和软删除。
- 实现 RAG 问答核心链路，包括关键词检索、Prompt 构建、DeepSeek/Mock Provider 路由、带引用回答和无上下文兜底。
- 设计 AI 调用日志与反馈评估模块，记录 Prompt、Token、耗时、召回 chunk 和失败状态，支持 Bad Case 复盘和质量改进。
- 使用 Redis 实现 JWT Logout 黑名单，使用 Flyway 管理数据库迁移，并接入 GitHub Actions CI。

## 一句话项目价值

这个项目不是简单调用大模型 API，而是把 AI 问答接入完整 Java 后端工程链路，重点体现认证、数据库设计、检索、Prompt、外部服务调用、可观测性和质量反馈。

## 面试关键词

RAG、Prompt Engineering、LLM Provider、DeepSeek API、JWT、Redis Token Blacklist、Flyway、MyBatis-Plus、Chunk、No-context Fallback、Token Usage、AI Observability、Bad Case Feedback、GitHub Actions

## 如何诚实说明 AI 辅助

如果面试官问是否使用 AI 辅助开发，可以这样说：

我使用 AI 辅助完成了部分代码实现、调试和文档整理，但项目的业务边界、表结构、接口设计、RAG 链路、日志可观测性、失败兜底和后续迭代方向是我自己理解并逐步验证的。每个核心模块我都能解释为什么这么设计，以及它目前的不足和下一步优化方向。

## 不建议写的说法

- 不要写“企业级生产系统”，可以写“面向 Java 后端面试作品集的工程化项目”。
- 不要写“完整 Agent 平台”，它现在更准确地说是“RAG 问答系统”。
- 不要写“已实现向量数据库和 rerank”，除非后续真的做完。
- 不要写“高并发系统”，当前重点不是压测和分布式高并发。

## 可以放在简历上的项目标题

```text
DevMind：AI 开发者知识库与 RAG 问答系统
```

## 可以放在 GitHub README 的短介绍

```text
An AI-powered developer knowledge base built with Spring Boot and Vue. It demonstrates a RAG-style Q&A pipeline with authentication, document chunking, prompt building, LLM provider abstraction, citations, token usage tracking, ask logs, fallback handling, and bad-case evaluation.
```
