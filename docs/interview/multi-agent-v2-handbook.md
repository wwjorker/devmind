# DevMind Multi-Agent v2 面试手册

## 一句话定位

DevMind 是一个 Java/Spring Boot 个人开发知识库：先用可评测 RAG 回答问题，再把
bad case 送入“只读归因 → 独立审查 → 人工批准 → 版本化执行 → 回归或补偿”的
受控修复闭环。

## 为什么需要两个 Agent

Evidence Triage 负责查明根因并在唯一允许的场景下提出 metadata patch；Change
Reviewer 使用独立上下文重新检查证据、遗漏反证、越权 diff、陈旧版本和 prompt
injection。它不是 Supervisor，也不拥有写权限。独立角色的价值必须由冻结 challenge
集证明；如果比 self-review 没多抓到至少 5 个缺陷，就不写“显著提升”。

## 为什么写权限在 Java 服务

模型输出是不可信候选数据。后端会再次验证：

- 固定 JSON 字段和长度；
- evidence 必须绑定当前用户真实存在的 document version；
- metadata patch 只能改 `tags/summary`；
- base version 必须仍匹配；
- Reviewer PASS 不等于批准；
- 人工决定和执行都使用幂等键；
- 乐观锁冲突返回可解释状态，不覆盖并发修改。

这样能在面试中明确区分“Agent 做判断”和“业务系统授予能力”。

## 事务与补偿怎么设计

外部 embedding、pgvector 和回归检索都在数据库事务外。一次执行分为：

1. `REQUIRES_NEW` 预留执行键并进入 `EXECUTING`；
2. 短事务写 metadata、创建不可变 version、归档 MySQL 旧向量；
3. 事务外重建向量和 serving index，进入 `VERIFYING`；
4. 目标问题命中后标记 `APPLIED`；
5. 失败则用 base version 写补偿版本，重建索引并标记 `ROLLED_BACK`；
6. 进程中断后，RecoveryService 扫描超时的 `EXECUTING/VERIFYING` 并按同一执行键恢复。

这里不是分布式事务。MySQL 文档、version、chunk 和向量 JSON 是事实源；pgvector
只是可重建索引。补偿失败会进入明确的 `FAILED/ROLLBACK_FAILED`，不声称强一致。

## Prompt 与引用可信性

模型输入和可观测 preview 是两个字段：模型接收完整多 chunk Prompt，日志/API
preview 最多 2,000 字符。引用由实际进入 Prompt 的 chunk 生成。schema v1 历史日志
可能存在“引用了模型没看到的 chunk”，因此不能进入回答归因；schema v2 才合格。

## 四臂评测怎么保证公平

四臂固定为 `rules`、`single`、`single+self-review`、`reviewed-multi`。模型臂使用同一
精确 provider/model、temperature 0、相同 case 顺序和预算；gold 字段不进入消息。
无效 JSON、超预算、超时和 Provider 失败都算失败，不为某一臂单独重试。报告保存
数据 SHA-256、knowledge snapshot、P50/P95、调用数、tokens 和按当次输入价格估算的
成本。

root-cause 分类、Reviewer challenge、目标修复和 40-case 检索回归是不同指标，不能
互相替代。首个 v1 Provider run 因 Reviewer 提示词退化为恒定接受而判无效，原始
报告仍被保留；修正协议后改用新措辞的 v2 冻结集，避免在同一结果上调参。v2 的
240 次真实调用无 Provider 失败，三个模型臂根因分类均为 24/24，challenge 缺陷
捕获分别为 single 12/12、self-review 8/12、reviewed-multi 10/12。独立 Reviewer
只比 self-review 多 2 个，未达到预注册的至少 5 个门槛，因此不能写成质量提升。
这些都是项目内部小样本，也不能声称统计显著或通用 Agent 优势。

## 最可能被追问的取舍

**为什么不用消息队列/Outbox？** 个人作品集规模下，持久化状态、幂等恢复和补偿已
能证明关键边界；Kafka 会增加运维与解释成本，却不提升当前求职证据。若进入多实例、
高吞吐和可靠异步任务，再引入 outbox + worker。

**为什么不让 Agent 改正文？** 正文改变事实语义，风险高于 metadata alias。首版只
自动执行可验证的低风险 metadata patch；document draft 只能交给人审。

**为什么 MySQL 还存向量？** 它是可恢复源数据和 JVM 对照路径；pgvector 提供 HNSW
serving。双写失败不回滚主事实，backfill 可从 MySQL 重建 serving index。

**为什么不是生产级？** 没有多实例调度、消息队列、企业级权限、可观测平台和大规模
统计评测。项目主动限制范围，重点证明 Java 事务边界、协议校验、幂等、恢复和评测。

**Claude/Fable 在项目里做了什么？** 只读设计/反向审核，不能修改代码，也不替代
Codex 对真实代码和测试的裁决。Phase B 实际 Claude CLI 模型是 opus-4-8，不冒充
Opus 5；Fable 不参与日常实现。

## 可展示的硬证据

- Java 17 / Spring Boot 3.3.5，公开 API 和 Vue 工作台可运行；
- MySQL 8 空库 Flyway V1–V13 实测；
- 多 chunk 超 2,000 字符完整模型输入回归测试；
- embedding 不在事务中的传播边界与失败行为测试；
- Agent run/step 持久化、模型/工具/token/超时预算；
- 跨租户读取拒绝、tool-call ID 与证据 ID 绑定；
- 人工审批幂等、文档乐观锁、版本归属；
- 执行恢复、补偿重放、前端冲突展示；
- 40-case Hit@3/MRR，以及带数据 hash、延迟、token、成本和失败门禁的真实四臂报告；
- 首次退化运行作为无效证据保留，修正版 v2 结果未达 Reviewer 增益门槛也如实披露。

## 不要说的夸大表述

- “生产级自治 Agent 平台”；
- “Multi-Agent 一定优于 single/self-review”；
- “分布式事务保证 MySQL 和 pgvector 强一致”；
- “评测结果统计显著”；
- “旧日志能证明模型忽略了正确证据”；
- “默认本地演示中的预置提案是模型现场生成”。
