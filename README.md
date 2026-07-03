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

当前版本已实现真实 dense embedding 接入（OpenAI 兼容 API，可插拔 provider）、rerank 精排（离线评估）以及 keyword / sparse-hybrid / dense-hybrid / dense-hybrid-rerank 四方检索评估。仍不宣称实现向量数据库（向量以 JSON 存于 MySQL）、rerank 线上问答链路、PDF/OCR 或生产级部署。默认配置全本地运行、零外部调用，不配置 API key 不产生任何费用。

## 项目亮点

```text
知识文档
-> TXT / Markdown 文件导入
-> 文档自动分块 chunks
-> 中英文关键词检索
-> chunk 向量持久化（本地稀疏向量 / 真实 dense embedding，按 provider 区分）
-> FULLTEXT + 关键词 + 向量 + RRF 融合检索
-> rerank 精排（可插拔，离线评估）
-> 无上下文兜底，减少幻觉
-> 构建带上下文的 Prompt
-> LLM Provider 路由
-> 返回带引用来源的回答
-> 记录成功 / 失败问答日志、token 用量和耗时
-> bad case 反馈
-> 四方检索策略 Hit@3 / MRR 对比评估
```

区别于只调用模型接口的 AI demo，DevMind 将 AI 问答接入认证、数据库设计、事务、Redis、日志、评估和 CI 等后端工程能力。

## 检索评估结果

在 14 篇文档、40 个人工标注 gold-label 用例（含词法失配、同义改写、跨主题 hard-negative）上，对四种检索策略计算 Hit@3 / MRR（K=3）：

| 策略 | Hit@3 | MRR | 特点 |
|---|---|---|---|
| keyword baseline | 0.886 | 0.814 | 关键词 / FULLTEXT，漏改写问题 |
| sparse-hybrid | 0.971 | 0.887 | 词法召回好 |
| dense-hybrid | 0.943 | **0.913** | 排序精度最强（最多命中第 1 位） |
| dense-hybrid-rerank | **1.000** | 0.867 | 最坏情况召回最强（相关文档必进前 3），但平均排序变平 |

核心结论：没有单一最优策略。dense embedding 的价值在精确排序（MRR 最高），rerank 的价值在最坏情况召回（Hit@3 满分），但 rerank 会把部分本来排第 1 的结果挤到第 2，拉低平均 MRR——选择取决于产品更在乎"答案一定在前 3"还是"答案通常排第 1"。评估集规模有限（40 条），结论为方向性而非统计显著。

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
./mvnw test        # Windows 用 .\mvnw.cmd test
./mvnw spring-boot:run
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
- 中英文关键词检索、MySQL FULLTEXT 与可解释 score
- chunk 向量持久化（本地稀疏向量与真实 dense embedding 按 provider 共存）、余弦相似度与 RRF 融合排序
- 可插拔 EmbeddingClient / RerankClient：默认本地零成本，配置 key 后可切换外部 embedding 与 rerank API
- 无上下文兜底，避免知识库没有资料时强行调用模型
- Prompt Preview 保存，方便排查 RAG 问题
- `LlmClient` 抽象，支持 Mock 与 DeepSeek Provider
- AI Ask 日志记录 provider、耗时、chunk ids、token usage、成功 / 失败状态
- bad case 反馈与评估汇总
- RAG evaluation dataset 覆盖率，用标准问题检查检索链路
- Retrieval evaluation 四方检索评估（keyword / sparse-hybrid / dense-hybrid / dense-hybrid-rerank），用人工标注 gold label 计算 Hit@3、MRR、首个相关片段排名与策略间 delta
- 前端展示问答、引用来源、召回片段、Prompt、日志详情和评估看板
- 后端单元测试与 GitHub Actions CI

## 核心设计要点

DevMind 的核心设计围绕 RAG 链路和后端工程化展开：

1. 文档先持久化并切分为 chunks，避免长文本直接进入 Prompt 导致上下文过长和召回不稳定。
2. 检索层支持中英文多关键词、标题、标签和类型召回，并保留可解释 score，便于排查召回结果。
3. chunk 重建时同步生成本地稀疏向量并持久化到向量表，提问时只计算 query 向量，再与已持久化的 chunk 向量做余弦相似度比较。
4. 混合检索使用 RRF 融合关键词 / FULLTEXT 排名和本地稀疏向量排名，避免直接相加不同量纲的分数。
5. 当检索不到有效上下文时，系统返回无上下文兜底，避免模型在知识库缺资料时编造答案。
6. Prompt Preview 与 Ask Log 会记录上下文、模型来源、召回片段、token 用量、耗时和状态，方便定位 RAG 问题。
7. `LlmClient` 抽象隔离业务流程和模型供应商，支持 Mock、本地测试、DeepSeek 接入和后续 Provider 扩展。
8. JWT logout 使用 Redis 黑名单保存未过期 token 的剩余 TTL，解决无状态 token 退出后仍可能可用的问题。
9. Flyway 管理数据库结构版本，避免不同环境手动执行 SQL 造成表结构漂移。
10. bad case feedback 和 RAG evaluation dataset 用于记录问题样例、期望答案和覆盖情况，形成持续优化闭环。
11. retrieval evaluation 会批量执行标准问题检索，用人工标注的相关文档作为 gold label 计算 Hit@3、MRR 和首个相关片段排名；命中关键词只作为诊断信息展示，不作为相关性裁判。
12. retrieval evaluation 在同一 gold-label 用例集上对比 keyword / sparse-hybrid / dense-hybrid / dense-hybrid-rerank 四种策略的 Hit@3、MRR 和 delta；外部依赖（dense embedding、rerank）未配置时对应策略标记为 unavailable 并优雅降级，评估不失败、不联网。
13. 评估集包含词法失配、同义改写和跨主题 hard-negative 用例；实测显示 dense embedding 能把改写问题的相关文档从第 5 位提升到第 1 位，rerank 能保证相关文档必进前 3，但会牺牲部分排序精度（详见上方评估结果表）。
14. embedding 输入文本由 `EmbeddingTextBuilder` 统一从标题、来源类型、标签和 chunk 内容构造，避免索引、重排和后续向量化使用不同字段。
15. 第一版优先支持 TXT / Markdown 导入，保证核心链路稳定后再扩展 PDF、Word、OCR 等解析能力。

## 项目定位

DevMind 当前定位是一个面向开发学习场景的 AI 知识库系统，重点展示 Java 后端工程链路中的 RAG 应用实践。

用于简历时可以简化为：

```text
DevMind：面向开发学习的 AI 知识库与 RAG 问答系统
基于 Spring Boot + Vue 3 实现知识文档管理、TXT/Markdown 导入、自动分块、关键词 + 向量混合检索（FULLTEXT / 稀疏向量 / 真实 dense embedding + RRF 融合 + rerank 精排）、Prompt 构造、DeepSeek 调用、引用来源追踪、token 成本观测、问答日志、bad case 反馈，以及基于人工 gold label 的四方检索策略 Hit@3/MRR 对比评估。
```

## 后续规划

后续迭代按优先级分为三类：

1. 检索增强：把向量存储从 MySQL JSON 迁移到真正的向量数据库（如 pgvector），rerank 从离线评估接入线上问答链路。
2. 评估增强：扩大标注评估集规模使结论具备统计显著性，补充 bad case 分类统计和模型回答质量评测。
3. 工程增强：接口限流、SSE 流式输出、PDF/Word 导入。

## 文档

- 后端说明：[backend/README.md](backend/README.md)
- 前端说明：[frontend/README.md](frontend/README.md)
- 架构说明：[backend/docs/architecture.md](backend/docs/architecture.md)
- API 调试：[backend/docs/api/devmind-api.http](backend/docs/api/devmind-api.http)

## CI

GitHub Actions 会在 push 和 pull request 时执行：

```text
backend: ./mvnw test
frontend: npm ci && npm run build
```

这可以证明项目不是只在本机“刚好能跑”，而是有基础的自动化验证。

## 说明

本项目用于学习和作品集展示，不在仓库中提交真实 API Key、数据库密码或个人隐私笔记。
