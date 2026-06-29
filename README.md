# DevMind：面向开发学习的 AI 知识库系统

[![CI](https://github.com/wwjorker/devmind/actions/workflows/ci.yml/badge.svg)](https://github.com/wwjorker/devmind/actions/workflows/ci.yml)

DevMind 是一个面向个人开发学习、项目复盘和面试准备的 AI 知识库系统。项目重点不是简单包装大模型 API，而是把 RAG 问答接入完整的 Java 后端工程链路。

## 当前状态

这是一个前后端分离的完整 monorepo 项目：

- `backend`：Spring Boot 后端，已实现认证、知识文档、检索、AI 问答、日志、反馈和评估接口。
- `frontend`：Vue 3 前端，已实现中文工作台、文档导入、AI 问答、引用来源、日志详情和评估看板。
- `CI`：GitHub Actions 已配置后端测试和前端构建。

已验证：

```text
backend: 单元测试通过
frontend: npm run build 通过
GitHub Actions: main 分支 CI 通过
```

当前版本不宣称已经实现向量检索、rerank、PDF/OCR 或生产级部署。第一版重点是先打通 Java 后端工程链路、RAG 问答闭环和质量评估流程。

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
-> RAG 评估集覆盖率、Hit@3 与 MRR
```

区别于只调用模型接口的 AI demo，DevMind 将 AI 问答接入认证、数据库设计、事务、Redis、日志、评估和 CI 等后端工程能力。

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

## 本地演示路径

正式展示前，可以先用 DBeaver 执行演示数据脚本：

```text
backend/docs/sql/reset-and-seed-demo-data-for-testuser.sql
```

这个脚本只重置 `testuser` 的 DevMind 演示数据，不会影响其他数据库或其他本地项目。

推荐演示顺序：

1. 登录 `testuser`。
2. 查看知识文档和自动生成的 chunks。
3. 提问：`面试中应该如何解释 Redis 缓存穿透？`
4. 查看回答、引用来源、召回片段、Prompt Preview 和 token 用量。
5. 提问：`Kubernetes Pod 驱逐策略是什么？`，展示无上下文兜底。
6. 打开评估看板，查看标准问题覆盖率、Hit@3、MRR 和问答日志。

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
- Retrieval evaluation 检索评估，用标准问题直接验证关键词解析、Hit@3、MRR、首个相关片段排名和期望知识点命中情况
- 前端展示问答、引用来源、召回片段、Prompt、日志详情和评估看板
- 后端单元测试与 GitHub Actions CI

## 核心设计要点

DevMind 的核心设计围绕 RAG 链路和后端工程化展开：

1. 文档先持久化并切分为 chunks，避免长文本直接进入 Prompt 导致上下文过长和召回不稳定。
2. 检索层支持中英文多关键词、标题、标签和类型召回，并保留可解释 score，便于排查召回结果。
3. 当检索不到有效上下文时，系统返回无上下文兜底，避免模型在知识库缺资料时编造答案。
4. Prompt Preview 与 Ask Log 会记录上下文、模型来源、召回片段、token 用量、耗时和状态，方便定位 RAG 问题。
5. `LlmClient` 抽象隔离业务流程和模型供应商，支持 Mock、本地测试、DeepSeek 接入和后续 Provider 扩展。
6. JWT logout 使用 Redis 黑名单保存未过期 token 的剩余 TTL，解决无状态 token 退出后仍可能可用的问题。
7. Flyway 管理数据库结构版本，避免不同环境手动执行 SQL 造成表结构漂移。
8. bad case feedback 和 RAG evaluation dataset 用于记录问题样例、期望答案和覆盖情况，形成持续优化闭环。
9. retrieval evaluation 会批量执行标准问题检索，输出通过率、Hit@3、MRR、首个相关片段排名、命中关键词、召回片段和缺失项，用来判断检索策略是否真的有效。
10. 第一版优先支持 TXT / Markdown 导入，保证核心链路稳定后再扩展 PDF、Word、OCR 等解析能力。

## 项目定位

DevMind 当前定位是一个面向开发学习场景的 AI 知识库系统，重点展示 Java 后端工程链路中的 RAG 应用实践。

用于简历时可以简化为：

```text
DevMind：面向开发学习的 AI 知识库与 RAG 问答系统
基于 Spring Boot + Vue 3 实现知识文档管理、TXT/Markdown 导入、自动分块、多关键词检索、Prompt 构造、DeepSeek 调用、引用来源追踪、token 成本观测、问答日志、bad case 反馈和 Hit@3/MRR 标准问题检索评估。
```

更详细的项目描述、简历 bullet 和面试讲法见：[backend/docs/resume-cn.md](backend/docs/resume-cn.md)。

## 后续规划

后续迭代按优先级分为三类：

1. 检索增强：BM25 / 全文检索、embedding、向量库、混合检索、rerank。
2. 评估增强：在当前 Hit@3/MRR 基线基础上补充更大的标准问题集、bad case 分类统计和模型回答质量评测。
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

本项目用于学习和作品集展示，不在仓库中提交真实 API Key、数据库密码或个人隐私笔记。
