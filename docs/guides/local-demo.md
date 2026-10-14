# 本地体验指南

先按[根目录运行说明](../../README.md#本地运行)启动服务。默认 mock 回答用于体验检索和界面，不代表真实模型的生成质量。

## 使用自己的文档

1. 注册并登录本地账号。
2. 创建文档，或导入 `.txt`、`.md`、`.markdown` 文件。可使用[示例知识笔记](../../backend/docs/samples/README.md)，单文件上限为 256 KB。
3. 导入 `redis-cache-penetration.md` 后，提问“Redis 缓存穿透是什么，怎么解决？”。
4. 查看回答、召回片段和引用；在问答日志中查看 provider、Prompt 预览、用量及反馈。

没有召回片段时，系统返回兜底回答并跳过模型。弱相关片段可能仍被召回，因此库外问题不保证触发兜底，详见[检索实验限制](../../backend/evaluation/retrieval-results.md)。

## 可选的完整演示数据

先在页面注册 `testuser`，密码由你设置。后端启动完成、Flyway 迁移执行后，在 MySQL 客户端连接本地 `devmind` 数据库，执行[演示种子 SQL](../../backend/docs/sql/reset-and-seed-demo-data-for-testuser.sql)。

**该脚本会删除并重建 `testuser` 名下的文档、问答日志、反馈与修复记录。** 只在专用演示账号中使用；自己的资料请放在另一个账号。这里是操作说明，启动脚本不会自动执行种子 SQL。

种子数据包含 14 篇文档、来源冲突案例和待审批的元数据修复提案。诊断和 Reviewer 结论是预置离线样例，不是刚刚调用模型生成的结果。

## 查看受控修复

登录 `testuser` 后打开“受控修复”：

1. 选择 `demo:source-conflict`，查看 `CONFLICT_PENDING` 状态和互相冲突的来源。该路径停止自动修复，交由人工判断。
2. 选择 `demo:metadata-repair-awaiting-approval`，核对目标文档、base version、diff、正反证据和回归问题。
3. 点击“批准原 diff”，提案进入 `APPROVED`；Reviewer 通过本身不会写入文档。
4. 点击“执行版本化发布、索引重建与目标复测”，查看提案状态、applied version 和回归结果。预置样例正常执行后为 `APPLIED`，关联 case 为 `RESOLVED`。

预置提案可在默认本地配置下审批和执行。重新发起 Triage / Reviewer 则需要真实 DeepSeek 配置，并可能产生调用费用；普通 mock 问答不模拟这两个 Agent，配置见[后端说明](../../backend/README.md#模型与检索配置)。

## 查看评估与排查问题

评估看板展示标准问题覆盖情况、反馈以及检索 Hit@3 / MRR。覆盖率表示是否问过标准问题，不等于回答正确率。开启远程 embedding / rerank 后，主动运行完整检索评估也可能产生外部调用。

- 后端未连接：检查 `8081` 端口和前端 `VITE_API_TARGET`。
- 数据未出现：确认先注册了 `testuser`，种子 SQL 连接的是当前后端所用数据库。
- 执行没有完成：保留 `errorCode`、执行结果和版本信息，按[架构说明](../../backend/docs/architecture.md#受控修复与恢复)检查失败阶段。
- 页面不能操作时，可使用[API 示例](../../backend/docs/api/devmind-api.http)检查同一接口。

完整实验结果及复现入口见[评估说明](../../backend/evaluation/README.md)。
