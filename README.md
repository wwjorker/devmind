# DevMind：面向 Java 后端求职的 AI 知识库系统

[![CI](https://github.com/wwjorker/devmind/actions/workflows/ci.yml/badge.svg)](https://github.com/wwjorker/devmind/actions/workflows/ci.yml)

DevMind 是一个面向个人开发学习、项目复盘和面试准备的 AI 知识库系统。项目重点不是简单包装大模型 API，而是把 RAG 问答接入完整的 Java 后端工程链路。

## 当前状态

这是一个用于 Java 后端求职展示的完整 monorepo 项目：

- `backend`：Spring Boot 后端，已实现认证、知识文档、检索、AI 问答、日志、反馈和评估接口。
- `frontend`：Vue 3 前端，已实现中文工作台、文档导入、AI 问答、引用来源、日志详情和评估看板。
- `CI`：GitHub Actions 已配置后端测试和前端构建。

已验证：

```text
backend: 29 个单元测试通过
frontend: npm run build 通过
GitHub Actions: main 分支 CI 通过
```

当前版本不宣称已经实现向量检索、rerank、PDF/OCR 或生产级部署。第一版重点是先把 Java 后端工程链路、RAG 问答闭环和质量评估意识讲清楚。

## 项目亮点

```text
知识文档
-> TXT / Markdown 文件导入
-> 文档自动分块 chunks
-> 中英文关键词检索
-> 无上下文兜底，减少幻觉
-> 构建带上下文的 Prompt
-> LLM Provider 路由
-> 返回带引用来源的回答
-> 记录成功 / 失败问答日志、token 用量和耗时
-> bad case 反馈
-> RAG 评估集覆盖率
```

它更适合作为 Java 后端简历项目，因为 AI 能力不是孤立 demo，而是和认证、数据库设计、事务、Redis、日志、评估、CI 等后端能力结合在一起。

## 项目结构

```text
devmind
+-- backend    Spring Boot 后端
+-- frontend   Vue 3 + Vite 前端
+-- .github    GitHub Actions CI
```

## 技术栈

后端：

```text
Java 17
Spring Boot 3.3.x
Spring Security
MyBatis-Plus
MySQL
Redis
Flyway
JJWT
Springdoc OpenAPI
DeepSeek API
```

前端：

```text
Vue 3
Vite
TypeScript
```

## 本地启动

后端：

```bash
cd backend
mvn test
mvn spring-boot:run
```

后端要求 Java 17。创建 `devmind` 数据库并配置本地 MySQL、Redis、模型环境变量后，启动后端服务，默认端口为 `8081`。

前端：

```bash
cd frontend
npm install
npm run dev
```

打开：

```text
http://127.0.0.1:5173
```

如果本地命令行仍然是 Java 8，可以先用 IntelliJ IDEA 打开 `backend`，在项目或运行配置里选择 Java 17 及以上的 JDK。推荐使用 Java 17，和 GitHub Actions 的 CI 环境保持一致。

## 快速演示路径

正式展示前，可以先用 DBeaver 执行演示数据脚本：

```text
backend/docs/sql/reset-and-seed-demo-data-for-testuser.sql
```

这个脚本只重置 `testuser` 的 DevMind 演示数据，不会影响其他数据库或 `F:\cangqiong` 项目。

推荐演示顺序：

1. 登录 `testuser`。
2. 查看知识文档和自动生成的 chunks。
3. 提问：`面试中应该如何解释 Redis 缓存穿透？`
4. 查看回答、引用来源、召回片段、Prompt Preview 和 token 用量。
5. 提问：`Kubernetes Pod 驱逐策略是什么？`，展示无上下文兜底。
6. 打开评估看板，查看标准问题覆盖率和问答日志。

## 核心功能

- JWT 登录认证与用户数据隔离
- BCrypt 密码加密
- Redis JWT logout 黑名单
- Flyway 数据库迁移管理
- 知识文档 CRUD 与软删除
- TXT / Markdown 笔记导入
- 文档创建、更新、导入后自动生成 chunks
- 中英文关键词检索与可解释 score
- 无上下文兜底，避免知识库没有资料时强行调用模型
- Prompt Preview 保存，方便排查 RAG 问题
- `LlmClient` 抽象，支持 Mock 与 DeepSeek Provider
- AI Ask 日志记录 provider、耗时、chunk ids、token usage、成功 / 失败状态
- bad case 反馈与评估汇总
- RAG evaluation dataset 覆盖率，用标准问题检查检索链路
- 前端展示问答、引用来源、召回片段、Prompt、日志详情和评估看板
- 后端单元测试与 GitHub Actions CI

## 面试可讲点

这个项目可以围绕下面几个问题展开：

1. 为什么 RAG 不是直接把问题发给大模型？
2. 文档为什么要切 chunks？
3. 检索 score 怎么计算，为什么说它可解释？
4. 没有检索到上下文时，为什么不调用 LLM？
5. Prompt Preview 和 Ask Log 为什么重要？
6. 为什么要抽象 `LlmClient`？
7. JWT logout 为什么需要 Redis 黑名单？
8. Flyway 比手动执行 SQL 好在哪里？
9. 怎么通过 bad case 和 evaluation dataset 持续改进 RAG？
10. 为什么第一版先支持 TXT / Markdown，而不是直接做 PDF/OCR？

## 简历定位

推荐简历表述：

```text
DevMind：面向开发学习的 AI 知识库与 RAG 问答系统
基于 Spring Boot + Vue 3 实现知识文档管理、TXT/Markdown 导入、自动分块、多关键词检索、Prompt 构造、DeepSeek 调用、引用来源追踪、token 成本观测、问答日志和 bad case 评估闭环。
```

更详细的简历 bullet 和面试讲法见：[backend/docs/resume-cn.md](backend/docs/resume-cn.md)。

## 后续规划

为了避免把项目包装得过度，当前 roadmap 按优先级分为三类：

1. 检索增强：BM25 / 全文检索、embedding、向量库、混合检索、rerank。
2. 评估增强：hit rate、MRR、标准问题批量评测、bad case 分类统计。
3. 工程增强：Provider fallback、接口限流、SSE 流式输出、PDF/Word 导入。

## 文档

- 项目方向护栏：[docs/project-direction-cn.md](docs/project-direction-cn.md)
- Java 17 环境说明：[docs/environment-jdk17-cn.md](docs/environment-jdk17-cn.md)
- 演示脚本：[docs/demo/devmind-demo-script-cn.md](docs/demo/devmind-demo-script-cn.md)
- 后端说明：[backend/README.md](backend/README.md)
- 前端说明：[frontend/README.md](frontend/README.md)
- 架构说明：[backend/docs/architecture.md](backend/docs/architecture.md)
- API 调试：[backend/docs/api/devmind-api.http](backend/docs/api/devmind-api.http)

## CI

GitHub Actions 会在 push 和 pull request 时执行：

```text
backend: mvn test
frontend: npm ci && npm run build
```

这可以证明项目不是只在本机“刚好能跑”，而是有基础的自动化验证。

## 说明

本项目用于 Java 后端求职作品集展示，不在仓库中提交真实 API Key、数据库密码或个人隐私笔记。
