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
- RAG 评估集覆盖率展示

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
2. 创建一篇 Redis 缓存穿透笔记，或者导入 `backend/docs/samples/redis-cache-penetration.md`。
3. 在 AI 问答区提问：`面试中应该如何解释 Redis 缓存穿透？`
4. 查看回答、引用来源、召回片段、提示词预览和 token 用量。
5. 提交“有帮助”或 bad case 反馈。
6. 打开评估看板，查看 bad case 和 RAG 评估集覆盖率。

更完整的展示顺序见根目录：[docs/demo/devmind-demo-script-cn.md](../docs/demo/devmind-demo-script-cn.md)。

## 无上下文兜底验证

可以提问：

```text
Kubernetes pod eviction policy 是什么？
```

如果知识库没有相关召回片段，后端会返回兜底回答，并跳过模型调用。

## 构建

```bash
npm run build
```

## 安全说明

前端不保存模型 API Key。DeepSeek 等模型密钥只应该配置在后端运行环境中。
