# DevMind：面向开发学习的 AI 知识库系统

[![CI](https://github.com/wwjorker/devmind/actions/workflows/ci.yml/badge.svg)](https://github.com/wwjorker/devmind/actions/workflows/ci.yml)

DevMind 是一个面向个人开发学习、项目复盘和知识沉淀的 AI 知识库系统。项目重点不是简单包装大模型 API，而是把 RAG 问答接入完整的 Java 后端工程链路。

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

当前版本已实现真实 dense embedding 接入（OpenAI 兼容 API，可插拔 provider）、rerank 精排（离线评估）、多策略检索评估，以及可选的 pgvector 向量存储：dense 向量双写 MySQL JSON（源数据，兼对照组）与 Postgres + pgvector HNSW（serving 索引，`docker compose` 一键启动，默认关闭）。仍不宣称实现 rerank 线上问答链路、PDF/OCR 或生产级部署。默认配置全本地运行、零外部调用，不配置 API key 不产生任何费用。

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

AI 问答不是独立模块，而是接入了认证、数据库设计、事务、Redis、日志、评估和 CI 的完整后端链路。

## 检索评估结果

在 14 篇文档、40 个人工标注 gold-label 用例（35 个正例含词法失配、同义改写、跨主题 hard-negative，5 个库外负例）上，对四种检索策略计算 Hit@3 / MRR（K=3，ngram FULLTEXT + 字面证据过滤生效后测得）：

| 策略 | Hit@3 | MRR | 特点 |
|---|---|---|---|
| keyword baseline | 0.857 | 0.843 | 字面证据优先，精确但漏改写问题 |
| sparse-hybrid | **1.000** | 0.924 | 向量通道补齐改写召回 |
| dense-hybrid | 0.971 | **0.936** | 排序精度最强（最多命中第 1 位） |
| dense-hybrid-rerank | **1.000** | 0.852 | 最坏情况召回满分，但平均排序变平 |

核心结论：没有单一最优策略。dense embedding 的价值在精确排序（MRR 最高），rerank 的价值在最坏情况召回（Hit@3 满分），但 rerank 会把部分本来排第 1 的结果挤到第 2，拉低平均 MRR。注意 rerank 是在混合检索 top-5 候选池上做"5 选 3"重排，其 Hit@3 满分受候选池限制；正例样本 35 条，Hit@3 相邻两档相差约 0.029，以上数字为方向性而非统计显著。

**词表消融**：问题解析里有一张人工技术短语表，为验证评估不依赖这张表，用 `DEVMIND_RETRIEVAL_TECH_PHRASES_ENABLED=false` 关掉后重跑：

| 策略 | 词表开 | 词表关 |
|---|---|---|
| keyword baseline | 0.857 / 0.843 | 0.714 / 0.686 |
| sparse-hybrid | 1.000 / 0.924 | 1.000 / 0.886 |

关键词基线确实吃词表红利（约 0.14），但混合检索的 Hit@3 不受影响——检索质量不依赖人工词表。

**向量 serving 规模基准**：另有一组暴力余弦 vs pgvector HNSW 的单机基准（1 万/10 万条、含 ef_search 召回-延迟权衡与近似检索的实测召回损失），见 [backend/README.md](backend/README.md) 的"向量 serving 规模基准"一节。

**负例与已知限制**：5 个库外问题中 1 个正确触发空召回兜底，其余 4 个仍被本地稀疏向量通道以 0.16–0.23 的弱相似度召回。校准数据显示，改写正例的 gold 相似度（约 0.14）低于 hard-negative 的噪声相似度（约 0.16），在 bigram 稀疏表示下两者发生倒挂，不存在能分开它们的阈值。这是稀疏表示的固有限制，也是引入 dense embedding、并计划基于 dense 相似度阈值改进无上下文判定的直接原因。

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
PostgreSQL + pgvector（可选向量索引）
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

需要演示数据时，先在 MySQL 中执行：

```text
backend/docs/sql/reset-and-seed-demo-data-for-testuser.sql
```

脚本只重置 `testuser` 的演示数据。

推荐体验顺序：

1. 登录 `testuser`。
2. 查看知识文档和自动生成的 chunks。
3. 提问：`Redis 缓存穿透是什么，怎么解决？`
4. 查看回答、引用来源、召回片段、Prompt Preview 和 token 用量。
5. 提问：`Kafka consumer rebalance 为什么会变慢？`，展示无上下文兜底。
6. 打开评估看板，查看标准问题覆盖率、Hit@3、MRR 和问答日志。

## 核心功能

- JWT 登录认证与用户数据隔离
- BCrypt 密码加密
- Redis JWT logout 黑名单
- Flyway 数据库迁移管理
- 知识文档 CRUD 与软删除
- TXT / Markdown 笔记导入
- 文档创建、更新、导入后自动生成 chunks
- 中英文关键词检索与 MySQL FULLTEXT，保留各通道得分用于调试
- chunk 向量持久化（本地稀疏向量与真实 dense embedding 按 provider 共存）、余弦相似度与 RRF 融合排序
- 可插拔 EmbeddingClient / RerankClient：默认本地零成本，配置 key 后可切换外部 embedding 与 rerank API
- 可选 pgvector 向量存储：dense 向量双写 MySQL JSON 与 Postgres HNSW 索引，评估接口可直接对比两种 serving 路径的 Hit@3 / MRR
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
2. 检索层支持中英文多关键词、标题、标签和类型召回，并保留各通道原始得分便于排查召回结果；RRF 融合后的分数只用于排序，不具备业务含义。
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

DevMind 是一个面向开发学习场景的 AI 知识库系统，重点展示 Java 后端工程链路中的 RAG 应用实践与可量化的检索质量评估。

## 后续规划

后续迭代按优先级分为三类：

1. 检索增强：rerank 从离线评估接入线上问答链路；无上下文判定从"召回为空"升级为基于 dense 相似度阈值；HNSW 参数（m / ef_search）按规模调优。
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

## 说明

本项目用于学习和作品集展示，不在仓库中提交真实 API Key、数据库密码或个人隐私笔记。
