# DevMind Frontend

这是 DevMind 的前端项目，使用 `Vue 3 + Vite + TypeScript` 实现，用于展示知识文档、AI 问答、问答日志和 RAG 评估结果。

## 功能

- 登录 / 注册
- 知识文档列表
- 手动创建知识文档
- 导入 `.txt / .md / .markdown` 笔记文件
- AI 问答
- 引用来源展示
- token 用量展示
- 无上下文兜底状态展示
- 问答日志列表
- 页面刷新后从后端 ask logs 恢复最近一次回答
- 问答日志详情：回答、提示词预览、token、召回片段、反馈
- 有帮助 / bad case 反馈提交
- RAG 评估集覆盖率、Hit@3 和 MRR 展示

## 本地启动

先启动后端：

```text
http://localhost:8081
```

再启动前端：

```bash
npm install
npm run dev
```

打开：

```text
http://127.0.0.1:5173
```

Vite dev server 会把 `/api` 请求代理到后端。

## 演示流程

1. 登录或注册本地账号。
2. 如果本地数据很乱，可以先在 DBeaver 执行 `backend/docs/sql/reset-and-seed-demo-data-for-testuser.sql`，为 `testuser` 重建一套标准演示数据。
3. 如果要演示文件导入，可以导入 `backend/docs/samples/redis-cache-penetration.md` 或其他 samples 目录下的 Markdown 笔记。
4. 在 AI 问答区提问：`面试中应该如何解释 Redis 缓存穿透？`
5. 查看回答、引用来源、召回片段、提示词预览和 token 用量。
6. 继续提问 JWT、Flyway、LlmClient 等工程设计问题，展示项目不是单纯 AI 套壳。
7. 提交“有帮助”或 bad case 反馈。
8. 打开评估看板，查看 bad case、RAG 评估集覆盖率、Hit@3 和 MRR。

更完整的展示顺序见根目录：[docs/demo/devmind-demo-script-cn.md](../docs/demo/devmind-demo-script-cn.md)。

## 无上下文兜底验证

可以提问：

```text
Kubernetes Pod 驱逐策略是什么？
```

如果知识库没有相关召回片段，后端会返回兜底回答，并跳过模型调用。

## 构建

```bash
npm run build
```

## 安全说明

前端不保存模型 API Key。DeepSeek 等模型密钥只应该配置在后端运行环境中。
