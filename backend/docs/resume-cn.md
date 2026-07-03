# DevMind 简历项目描述

## 简历标题

```text
DevMind：面向开发学习的 AI 知识库与 RAG 问答系统
```

## 技术栈

```text
Java 17、Spring Boot、Spring Security、JWT、BCrypt、Redis、MyBatis-Plus、MySQL、Flyway、Maven、Springdoc OpenAPI、DeepSeek API、Vue 3、Vite、TypeScript、GitHub Actions
```

## 一句话项目介绍

```text
基于 Spring Boot 和 Vue 3 设计并实现开发学习知识库系统，支持知识文档管理、Markdown/TXT 导入、自动分块、关键词 + 向量混合检索（MySQL FULLTEXT / 本地稀疏向量 / 真实 dense embedding + RRF 融合 + rerank 精排）、RAG 问答、DeepSeek 模型调用、引用来源追踪、token 成本观测、问答日志、bad case 反馈，以及基于人工 gold label 的四方检索策略 Hit@3/MRR 对比评估。
```

## 当前可投递版本

当前版本适合写进 Java 后端简历，但表述要准确：

```text
已完成：认证、文档管理、文件导入、自动分块、MySQL FULLTEXT 检索、多关键词与元数据召回、本地稀疏向量持久化、真实 dense embedding 接入、rerank 精排、RRF 融合排序、RetrievalStrategy / EmbeddingClient / RerankClient 抽象、keyword/sparse/dense/rerank 四方 Hit@3-MRR 检索评估、Prompt 构造、DeepSeek/Mock Provider、Provider fallback、引用来源、token 统计、问答日志、bad case 反馈、前后端联调和 GitHub Actions CI。

未完成：向量数据库（当前向量以 JSON 存于 MySQL）、rerank 接入线上问答链路（当前仅离线评估）、更大规模标注评估集、PDF/OCR、SSE 流式输出、生产级部署。
```

面试时可以主动说“我先做 MySQL FULLTEXT 与可解释关键词检索，把完整后端链路和评估闭环跑通；再抽象 RetrievalStrategy / EmbeddingClient / RerankClient，接入真实 dense embedding 和 rerank，并在同一套 gold-label 评估集上做了 keyword/sparse/dense/rerank 四方 Hit@3/MRR 对比，量化出各策略的权衡；下一步是把向量存储从 MySQL JSON 换成真正的向量库”。这比把项目包装成生产级 AI Agent 更稳。

## 简历 Bullet 版本

- 设计用户级知识库数据模型，实现知识文档 CRUD、软归档、Markdown/TXT 导入、自动 chunk 生成与文档更新后的 chunk 重建机制。
- 实现 RAG 问答链路，包括问题关键词解析、chunk 检索、Prompt 构造、LLM 调用、答案返回和 citations 引用来源追踪。
- 抽象 `RetrievalStrategy` / `EmbeddingClient` / `RerankClient` 三层可插拔接口，实现 MySQL FULLTEXT + 多关键词 baseline、本地稀疏向量与真实 dense embedding（OpenAI 兼容 API）双路持久化、RRF 融合排序与 rerank 精排；默认全本地零外部调用，配置 key 后可切换外部 provider，不配 key 不产生费用。
- 抽象 `LlmClient` 与 `LlmClientRouter`，支持 Mock 与 DeepSeek Provider 切换，降低模型调用与业务编排耦合，便于本地测试和后续扩展其他模型。
- 实现 Provider fallback：真实模型调用失败时先记录失败日志，再降级到本地 Mock Provider 返回带引用来源的兜底回答，避免接口直接 500。
- 基于 Spring Security、JWT、BCrypt 和 Redis blacklist 实现认证与退出登录，退出时将 token 写入 Redis 并设置剩余 TTL，避免未过期 token 继续访问。
- 设计 `ai_ask_log` 问答日志，记录 provider、prompt preview、召回 chunk、token usage、耗时和成功/兜底/失败状态，用于成本观测与问题排查。
- 设计 `ai_ask_feedback` bad case 反馈与四方检索评估（keyword / sparse-hybrid / dense-hybrid / dense-hybrid-rerank）：基于 14 篇文档、40 个人工标注 gold-label 用例（含词法失配、同义改写、跨主题 hard-negative）计算 Hit@3/MRR，量化发现无单一最优——dense embedding 排序精度最高（MRR 0.81→0.91），叠加 rerank 后 top-3 召回满分（Hit@3 1.0）但 MRR 回落至 0.87，揭示"精确排序 vs 最坏情况召回"的权衡；期望关键词仅作诊断展示、不作相关性裁判。
- 引入 Flyway 管理数据库结构版本，并配置 GitHub Actions 执行后端测试和前端构建，提升项目初始化、协作和持续集成的工程化程度。

## 更短版本

- 基于 Spring Boot + Vue 3 实现开发学习知识库与 RAG 问答系统，支持文档导入、自动分块、关键词 + 稀疏/dense 向量混合检索、RRF 融合、rerank 精排、Prompt 构造、DeepSeek 调用和引用来源展示。
- 抽象 LlmClient / EmbeddingClient / RerankClient 三层 Provider，默认本地零成本、配 key 可切外部 API，并记录 token usage、耗时、召回 chunk 和模型来源，实现 AI 调用可观测。
- 构建 gold-label 检索评估体系：40 个人工标注用例上对比 keyword / sparse / dense / rerank 四方 Hit@3/MRR（dense MRR 0.81→0.91，rerank Hit@3 1.0），量化揭示排序精度与最坏情况召回的权衡。

## 面试主线

面试时不要从“我调了 DeepSeek API”开始讲，而是按这条线讲：

```text
我做的是一个 Java 后端知识库系统，AI 问答只是其中一条业务链路。

用户先录入或导入知识文档，系统自动分块；
提问时先解析关键词，再检索相关 chunk；
检索结果会综合内容、标题、标签和类型打分，并对重复片段降权；混合策略再用 RRF 融合关键词排序和本地稀疏向量排序；
随后系统构造 Prompt，路由到 Mock 或 DeepSeek Provider；
如果真实模型调用失败，会先记录失败日志，再降级到本地 Mock Provider；
模型回答后返回引用来源、token 用量和耗时；
如果回答不好，用户可以提交 bad case feedback；
最后评估看板会统计标准问题覆盖率、按人工标注相关文档计算的 Hit@3、MRR 和 bad case 情况。
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

### 3. 有可解释检索

当前检索不是只做简单 LIKE，而是会综合：

```text
chunk 内容匹配
文档标题匹配
文档标签匹配
文档类型匹配
重复内容降权
```

这样面试时可以讲清楚“为什么召回这些 chunk”“分数怎么来”“为什么暂时没有先上向量库”。

### 4. 有可观测性

问答日志记录：

```text
prompt preview
model provider
retrieved chunk ids
elapsed ms
prompt tokens
completion tokens
total tokens
success / fallback / failure status
```

这能用于排查回答质量问题，也能分析模型调用成本。

### 5. 有质量反馈闭环

反馈表和评估集记录：

```text
standard evaluation question
expected answer
retrieved chunks
helpful / bad case
reason
Hit@3
MRR
keyword baseline vs hybrid/RRF delta
first relevant rank
matched / missing keywords
```

这说明项目考虑了后续 RAG 迭代，而不是只做一次性 demo。

## 可继续优化点

第一阶段：先把项目稳定成可投递版本。

- 统一本地 JDK 17 环境，确保 IDEA、本地命令行和 GitHub Actions 一致。
- 清理演示数据和 README 截图，让仓库首页像正式项目。
- 用中文标准问题跑完 RAG 评估集，形成可展示的覆盖率、Hit@3 和 MRR 结果。

第二阶段：增强检索质量。

- 增加 BM25 或全文检索能力。
- 接入外部 embedding 和向量数据库。
- 将当前本地稀疏向量混合检索升级为外部 embedding provider 与 vector store。
- 增加专业 rerank。
- 基于当前 keyword baseline 与 hybrid/RRF 的 Hit@3/MRR 基线，对比外部 embedding、向量库和 rerank 的提升。

第三阶段：增强工程深度。

- 增加限流、接口幂等和失败重试。
- 支持 PDF/Word 文档解析。
- 增加 SSE 流式输出。

## 不建议这样写

不要写成“AI Agent 平台”“生产级智能体系统”“企业知识库商业产品”。当前项目的真实定位是：

```text
面向 Java 后端求职的 AI 知识库项目，重点展示后端工程能力 + RAG 应用链路 + 质量评估意识。
```
