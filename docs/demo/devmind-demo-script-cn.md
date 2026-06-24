# DevMind 演示脚本

这份文档用于面试前自测和录屏演示。演示目标不是炫界面，而是让面试官看到：系统真的跑通了 RAG 链路，并且能解释回答来源、Token、日志和反馈。

## 启动前检查

后端：

```text
http://localhost:8081
```

前端：

```text
http://127.0.0.1:5173
```

数据库：

```text
MySQL database: devmind
Redis: local Redis for JWT logout blacklist
```

模型：

```text
DEVMIND_AI_PROVIDER=mock      本地演示稳定，不消耗 API
DEVMIND_AI_PROVIDER=deepseek  真实模型演示，需要 API Key
```

## 演示路线

### 1. 打开前端

打开：

```text
http://127.0.0.1:5173
```

可以说：

这是 DevMind 的前端演示页，左侧是模块导航，中间维护知识文档，右侧是 AI Ask 和日志评估。

### 2. 创建知识文档

创建一篇 Redis 缓存穿透复盘：

```text
Title:
Redis cache penetration review

Type:
bug_review

Tags:
redis,cache,backend

Content:
Problem:
The API may hit the database repeatedly when a missing key is requested many times.

Root cause:
The system only caches existing data, so non-existing data bypasses Redis every time.

Solution:
Cache empty values for a short TTL, validate illegal parameters early, and add rate limiting for abnormal traffic.

Interview talking point:
Cache penetration is different from cache breakdown and cache avalanche. The key is to protect the database from repeated misses.
```

可以说：

文档保存后，后端会自动把内容切成 chunk。之后 RAG 检索不是直接查整篇文档，而是查更细粒度的 chunk。

### 3. 提问一个有上下文的问题

问题：

```text
How should I explain Redis cache penetration in an interview?
```

观察结果：

- 状态是 Success。
- Provider 是 Mock 或 DeepSeek。
- 有 keyword。
- 有 chunk 数量。
- 有 Prompt / Completion / Total Token。
- 有 Citations。

可以说：

这个回答不是直接让模型自由发挥，而是先从知识库里召回 Redis 相关 chunk，再把上下文放进 Prompt，所以回答下面能看到引用来源。

### 4. 打开日志详情

在 Ask Logs 里点 Details。

重点看：

- Answer
- Prompt Preview
- Retrieved Chunks
- Score
- Token
- Chunk ids

可以说：

我保存 Prompt Preview 是为了排查问题。只看最终答案不知道模型看到了什么上下文，保存 Prompt 后可以判断问题出在数据、检索、Prompt 还是模型生成。

### 5. 提问一个知识库没有的问题

问题：

```text
What is Kubernetes pod eviction policy?
```

观察结果：

- 如果没有相关文档，系统返回 fallback。
- Chunks 是 0。
- 没有 citations。

可以说：

这里系统没有硬编答案，而是明确说知识库没有足够信息。这是为了减少幻觉，也能节省模型调用成本。

### 6. 保存 Bad Case

在回答下面填写 bad case 原因，例如：

```text
The answer is acceptable, but this test marks it as a bad case for evaluation.
```

然后点击 Save bad case。

可以说：

Bad Case 反馈用于后续迭代检索、Prompt 和知识库内容。RAG 项目不是一次写完就结束，需要靠失败样例持续改进。

### 7. 查看 Evaluation

打开 Evaluation 页面。

重点看：

- bad case 数量
- bad case rate
- recent bad cases
- ask logs

可以说：

这个页面是轻量评估看板。现在还不是完整离线评测集，但已经能把线上反馈沉淀下来，为后续做评估集和检索优化提供数据。

## 演示时最重要的三句话

1. 这个项目不是薄 AI API 包装，而是完整 Java 后端链路。
2. 我重点做了 RAG 的可解释性和可观测性：引用、Prompt、Token、日志、Bad Case。
3. 当前检索还是关键词版本，后续可以升级到向量检索、混合检索和 rerank。

## 常见问题排查

### 前端刷新后回答没了

这是正常的。当前右侧 Ask Result 是本次页面状态，刷新后不会自动保留。历史回答在 Ask Logs 里，可以点击 Restore 或 Details 恢复查看。

### 提问后没有引用

说明没有召回到相关 chunk。可能原因：

- 知识库没有相关文档。
- 关键词和文档内容不匹配。
- 文档被归档。

### DeepSeek 调用失败

检查：

- `DEVMIND_AI_PROVIDER=deepseek`
- `DEVMIND_DEEPSEEK_API_KEY` 是否配置。
- API Key 是否还有余额。
- 网络是否能访问 DeepSeek API。

如果只是面试演示，可以切回 Mock Provider，先保证链路稳定。

### 数据库没有表

确认：

- MySQL 有 `devmind` 数据库。
- 后端启动日志里 Flyway 是否执行成功。
- IDEA 环境变量里的数据库用户名和密码是否正确。

### Redis 没启动

Logout 黑名单依赖 Redis。如果 Redis 没启动，认证相关功能可能报错。可以启动本地 Redis，或者只演示文档和 AI Ask 主链路。
