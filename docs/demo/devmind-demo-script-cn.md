# DevMind 演示脚本

这份脚本用于面试前自测和项目展示。目标不是把所有按钮都点一遍，而是稳定展示“后端工程 + RAG 闭环”的主线。

## 1. 启动顺序

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

### 步骤 2：导入中文笔记

在“知识文档”区域导入：

```text
backend/docs/samples/redis-cache-penetration.md
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
```

### 步骤 4：提问无资料问题

输入：

```text
Kubernetes Pod 驱逐策略是什么？
```

如果知识库没有 Kubernetes 笔记，系统应该返回无上下文兜底。

可讲点：

```text
当检索不到上下文时，系统不强行编答案，这属于 RAG 幻觉控制。
```

### 步骤 5：查看评估看板

打开“评估看板”，观察：

- RAG 评估集覆盖率。
- 哪些标准问题已被问过。
- 问答日志。
- 每条日志的 provider、chunk 数、耗时、详情。

可讲点：

```text
我不是只看模型有没有回答，而是用标准问题和历史日志检查检索覆盖情况。
后续可以基于 bad case 继续优化 Prompt、检索策略和文档内容。
```

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
-> 通过 feedback 和 evaluation dataset 做质量闭环
```

## 4. 不要这样演示

- 不要一上来讲“我接了 DeepSeek API”。
- 不要只展示回答效果，不展示日志、引用和评估。
- 不要把历史英文日志当成问题解释太久；历史日志只是测试记录，正式演示以中文问题为主。
- 不要承诺已经实现向量检索、rerank、PDF/OCR。当前版本主打可解释关键词检索和完整后端闭环。

## 5. 如果被问到同质化

可以这样回答：

```text
知识库问答本身确实常见，所以我没有只做聊天页面。
这个项目重点放在 Java 后端工程链路上：认证、数据隔离、文档分块、检索、Prompt、Provider 抽象、日志、token 成本、bad case 和评估集。
它的价值不是“我也做了一个知识库”，而是我能解释每个后端模块为什么这样设计，以及后续怎么升级到混合检索和离线评估。
```

