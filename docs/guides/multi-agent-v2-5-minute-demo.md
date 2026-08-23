# DevMind Multi-Agent v2：5 分钟演示脚本

这条演示路径展示的是“可审计、需人工批准、失败可补偿”的个人知识库修复闭环，
不是生产级自治 Agent 平台。默认本地模式不调用外部模型，也不会产生模型费用。

## 演示前准备

1. 启动本地 MySQL、Redis、后端和前端，注册一次 `testuser`。
2. 在 MySQL `devmind` 库执行
   `backend/docs/sql/reset-and-seed-demo-data-for-testuser.sql`。
3. 重新登录 `testuser`，打开“受控修复”。

种子脚本可重复执行，只重置 `testuser`。它预置的诊断和 Reviewer 结论是离线
演示 fixture，不应描述成“模型刚刚生成”；真实 Triage/Reviewer 按钮需要配置
DeepSeek。

## 0:00–0:45：先讲问题，不先讲框架

话术：

> RAG 上线后，最难的不是再包一层模型 API，而是解释 bad case 到底来自召回、
> 知识缺失、来源冲突还是生成错误。DevMind 把一次差评保存为带问答和 chunk
> 快照的 bad case，再由只读 Triage 和独立 Reviewer 给出受约束提案。

展示“AI 问答”中 `Redis 缓存穿透是什么，怎么解决？` 的回答、可见引用和日志。
指出模型接收完整 Prompt，界面只显示有限 preview，schema v2 日志才允许做
“证据正确但回答错误”的归因。

## 0:45–1:45：展示来源冲突不自动写

在“受控修复”选择 `demo:source-conflict`：

- 状态为 `CONFLICT_PENDING`；
- 页面明确显示“自动修复已停止”；
- 展开模型可见 chunk 快照与用户可信来源；
- 两条来源对固定 TTL 与续期给出不兼容结论。

话术：

> Agent 没有被授权替用户决定权威来源。冲突路由是终态，只展示证据给人审，
> 当前版本故意没有增加一个看似完整、实际无法证明安全的自动裁决接口。

## 1:45–3:35：执行一个受控 metadata 修复

选择 `demo:metadata-repair-awaiting-approval`：

1. 核对目标文档、base version 1、提议 diff、版本化来源证据、反证和回归问题。
2. 展开 Reviewer，说明 Reviewer 通过仍不会写库。
3. 点击“批准原 diff”，状态变为 `APPROVED`。
4. 点击“执行版本化发布、索引重建与目标复测”。
5. 刷新后确认 `APPLIED`、applied version 2 和目标文档 top-3 回归结果。

话术：

> Java 服务而不是模型掌握最终权限：审批幂等键、白名单 JSON schema、文档乐观锁
> 和版本归属都由后端校验。MySQL metadata 写入在短事务中完成；embedding 和
> serving index 重建在事务外。索引或目标回归失败时，系统基于 version 1 生成
> 补偿版本并恢复派生索引，而不是把长 HTTP 调用包进数据库事务。

## 3:35–4:30：展示可复现证据

打开代码或测试报告，指出：

- MySQL 8 Testcontainers 从空库执行 V1–V13；
- 同一个真实 MySQL 测试覆盖超时执行恢复、崩溃后补偿收尾和 demo seed 两次重放；
- H2 用于快速测试，但不替代 MySQL migration gate；
- MySQL JSON 向量是源数据，pgvector 是可重建 serving index。

Docker 29 环境的复现命令见 `backend/evaluation/README.md`，需要
`-Dapi.version=1.44`。

## 4:30–5:00：诚实收尾

话术：

> 四臂协议固定为 rules、single、single+self-review、reviewed-multi。真实 DeepSeek
> 在修正版 v2 冻结集上完成 240 次调用，三个模型臂的根因分类都是 24/24；challenge
> 缺陷捕获依次是 12/12、8/12、10/12。独立 Reviewer 比 self-review 多抓 2 个，
> 没达到预注册的至少 5 个门槛，所以我不宣称 Reviewer 带来质量提升。这个项目能
> 证明的是受控执行、证据绑定、恢复和可复现评测方法，不是生产规模或统计泛化。

## 演示失败时的降级顺序

- 外部模型不可用：使用预置的已审提案，明确它是 fixture。
- Redis 不可用：不演示登录/限流，直接展示 MySQL/Testcontainers 测试证据。
- 前端异常：用 `backend/docs/api/devmind-api.http` 演示同一公开 API。
- 执行结果非 `APPLIED`：保留现场，展示 `errorCode`、`executionResultJson` 和版本，
  把它作为恢复/补偿设计的真实排查案例，不手改数据库伪造成功。
