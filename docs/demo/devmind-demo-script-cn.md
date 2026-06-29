# DevMind 演示脚本

这份脚本用于面试前自测和项目展示。目标不是把所有按钮都点一遍，而是稳定展示“后端工程 + RAG 闭环”的主线。

## 1. 启动顺序

如果本地数据已经被多次测试污染，先在 DBeaver 执行演示数据重置脚本：

```text
backend/docs/sql/reset-and-seed-demo-data-for-testuser.sql
```

注意：这个脚本只清理并重建 `testuser` 的 DevMind 演示数据，不会删除其他用户，也不会影响其他本地项目。

先启动后端：

```text
F:\AI项目\devmind\backend
DevMindApplication
```

再启动前端：

```bash
cd F:\AI项目\devmind\frontend
npm run dev
```

打开：

```text
http://127.0.0.1:5173
```

## 2. 演示主线

### 步骤 1：登录

使用本地测试账号登录。这个步骤说明系统不是匿名 demo，而是有用户级数据隔离。

### 步骤 2：准备中文知识文档

正式演示建议使用 SQL 脚本提前准备好下面 6 类材料：

- Redis 缓存穿透复盘
- JWT 退出登录与 Redis 黑名单
- Flyway migration 数据库迁移
- LlmClient 与 LLM Provider 抽象
- RAG 无上下文兜底
- RAG 回答质量评估

如果想演示“文件导入”能力，可以在“知识文档”区域手动导入：

```text
backend/docs/samples/redis-cache-penetration.md
backend/docs/samples/jwt-logout-redis-blacklist.md
backend/docs/samples/flyway-migration.md
backend/docs/samples/llm-provider-abstraction.md
backend/docs/samples/no-context-fallback.md
backend/docs/samples/rag-quality-evaluation.md
```

导入后端会创建知识文档，并自动生成 chunks。

可讲点：

```text
文档不是直接丢给模型，而是先落库，再切成可检索的 chunk。
```

### 步骤 3：提问 Redis 缓存穿透

在“AI 问答”输入：

```text
面试中应该如何解释 Redis 缓存穿透？
```

观察回答、引用来源、召回 chunk、Prompt Preview 和 token 用量。

可讲点：

```text
系统先检索相关 chunk，再把上下文放进 Prompt，最后调用 LLM。
回答旁边能看到 citations，说明答案不是完全凭空生成。
当前检索不是单关键词硬搜，而是会抽取多个中英文关键词；chunk 正文走 MySQL FULLTEXT 相关性召回，同时保留关键词 LIKE 兜底，并利用标题、标签和类型做元数据召回。
如果多篇笔记内容重复，系统会对后续重复片段降权，避免同一段材料占满全部引用。
```

### 步骤 4：提问后端工程问题

继续问几个能展示“不是单纯 AI 套壳”的问题：

```text
JWT 退出登录为什么需要 Redis 黑名单？
```

```text
这个项目里 Flyway migration 解决了什么问题？
```

```text
为什么 DevMind 要抽象 LlmClient，而不是直接调用 DeepSeek？
```

可讲点：

```text
这些问题不只是测试模型效果，而是展示后端工程设计：认证安全、数据库迁移、接口抽象和 Provider 解耦。
```

### 步骤 5：提问无资料问题

输入：

```text
Kubernetes Pod 驱逐策略是什么？
```

如果知识库没有 Kubernetes 笔记，系统应该返回无上下文兜底。

可讲点：

```text
当检索不到上下文时，系统不强行编答案，这属于 RAG 幻觉控制。
```

### 步骤 6：查看评估看板

打开“评估看板”，观察：

- 检索评估通过率、Hit@3 和 MRR。
- 每个标准问题实际解析出的检索词。
- 检索命中的关键词、召回片段数和 Top 文档。
- RAG 评估集覆盖率。
- 哪些标准问题已被问过。
- 问答日志。
- 每条日志的 provider、chunk 数、耗时、详情。

可讲点：

```text
我不是只看模型有没有回答，而是用标准问题直接跑检索，用人工标注的相关文档作为 gold label 检查 Hit@3、MRR 和首个相关片段排名；关键词命中只作为诊断信息。
历史问答日志用于看真实请求的 provider、token、耗时和引用来源。
后续可以基于 bad case 继续优化 Prompt、检索策略和文档内容。
```

如果已经执行过上面的标准问题，评估集覆盖率会从 0/8 逐步提升，Hit@3 和 MRR 会反映相关证据是否进入 Top 3 以及排名是否靠前。Kubernetes 问题即使被覆盖，也应该是 0 chunk 的无上下文兜底，这是负样例，不是失败。

## 3. 面试讲解顺序

推荐按这条线讲：

```text
用户登录
-> 导入知识文档
-> 自动切分 chunk
-> 提问时检索相关 chunk
-> 构建带上下文的 Prompt
-> 路由到 Mock 或 DeepSeek Provider
-> 返回答案和 citations
-> 记录 ask log、token、耗时、召回 chunk
-> 通过 feedback、evaluation dataset 和 retrieval evaluation 做质量闭环
```

## 4. 不要这样演示

- 不要一上来讲“我接了 DeepSeek API”。
- 不要只展示回答效果，不展示日志、引用和评估。
- 不要把历史英文日志当成问题解释太久；历史日志只是测试记录，正式演示以中文问题为主。
- 不要承诺已经实现向量检索、rerank、PDF/OCR。当前版本主打 MySQL FULLTEXT/BM25-style 相关性、可解释关键词检索、元数据召回、重复片段降权和完整后端闭环。

## 5. 如果被问到同质化

可以这样回答：

```text
知识库问答本身确实常见，所以我没有只做聊天页面。
这个项目重点放在 Java 后端工程链路上：认证、数据隔离、文档分块、检索、Prompt、Provider 抽象、日志、token 成本、bad case 和标准问题检索评估。
它的价值不是“我也做了一个知识库”，而是我能解释每个后端模块为什么这样设计。
例如检索层不是只查正文，还会把标题、标签、类型作为候选召回，并对重复 chunk 降权；后续再升级到 BM25、混合检索和离线评估。
```
