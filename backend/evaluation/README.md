# 实验方法与结果

这里保存检索评估、独立 Reviewer 对比，以及受控修复的实验协议和结果。**所有已有数字都是历史记录，本次文档整理没有重新运行模型、数据库实验或性能测试。** 各实验使用的环境、快照与评价对象不同，不能拼成一份当前版本验收结果。

## 结果入口

| 实验 | 依据 | 结论与限制 |
| --- | --- | --- |
| 五策略检索、词表消融、向量规模基准 | [检索实验记录](retrieval-results.md) | 保留旧 README 的数字；缺少完整运行清单与独立原始报告，仅作历史参考。 |
| 独立 Reviewer 四臂对比，2026-08-23 | [结果说明](results/phase-d-four-arm-provider-run-v2-2026-08-23.md) · [原始 JSON](results/phase-d-four-arm-provider-run-v2-2026-08-23.json) | 独立 Reviewer 比 self-review 多捕获 2 个缺陷，未达预注册的 +5 门槛；也未超过 single，不能宣称质量提升。 |
| 受控修复前后检索，2026-08-23 | [结果说明](results/phase-d-controlled-repair-run-2026-08-23.md) · [原始 JSON](results/phase-d-controlled-repair-run-2026-08-23.json) | 一个确定性修复样例通过；40 条用例的 sparse Hit@3 / MRR 前后不变，不是通用修复成功率。 |
| Tool Calling 协议，2026-08-23 | [历史 smoke 记录](results/phase-d-deepseek-tool-calling-smoke-2026-08-23.md) | 只验证两轮模型与工具消息交互，不衡量检索或修复质量。 |

四臂修正版 v2 共完成 240 次真实模型调用，无 provider 调用失败；三个模型臂根因分类均为 24/24。缺陷捕获为 single 12/12、self-review 8/12、reviewed-multi 10/12，后者还有一条字段矛盾的未评分响应。它是小型内部数据集上的 **prompt-only 离线对比**，所有实验臂工具集为空，没有执行生产编排器、证据工具、审批和修复流程，不能据此给端到端 Agent 质量打分。费用为当时记录的价目表估算，不是账单。

原始协议与报告中的 `resumeClaimAllowed` 是历史字段名，表示预注册结论门槛；该值仍为 `false`。原始 JSON 保持不变。

字节校验需区分 Git 中的 LF 与 Windows 检出后的 CRLF：受控修复报告的记录哈希匹配 LF，四臂 v2 的记录哈希匹配 CRLF。无效首跑的历史哈希与两者均不匹配，原始摘要尚未核实，详见其结果说明；没有据此改写 JSON 或修正协议中的历史字段。新实验应记录实际输入文件字节的哈希。

## 数据集与冻结协议

- [40 条检索用例](../src/main/resources/evaluation/v1-retrieval-cases.json)：35 个正例按 gold 文档标题计算 Hit@3 / MRR，5 个库外负例单独判断空召回；应用启动时加载校验。
- [预注册协议](v2-preregistered-protocol.json)：固定 rules、single、single+self-review、reviewed-multi 四臂，规定公平预算、指标、安全门槛和 Reviewer 提升阈值。
- [6 条早期开发样例](v2-development-bad-cases-v0.1.json)与[12 条开发集](v2-development-bad-cases-v1.json)：标签可见，用于协议与提示词开发，不计入保留集成绩。
- [v1 根因保留集](v2-sealed-bad-cases-v1.json)与[v1 Reviewer challenge](v2-reviewer-challenges-v1.json)：各 24 条，保留首次实验的数据版本。文件名 sealed 不证明模型从未见过材料。
- [v2 根因保留集](v2-sealed-bad-cases-v2.json)与[v2 Reviewer challenge](v2-reviewer-challenges-v2.json)：协议修正后、首次评分运行前冻结的 2.0.0 数据，使用新 ID 和措辞。
- [运行清单模板](v2-run-manifest-template.json)：记录精确模型、知识快照、数据哈希、预算和指标。模板中的 null 是未填写值，不能当成测量结果。

根因集覆盖六类问题；challenge 包含 12 个有效提案和 12 个缺陷提案。运行器在构造模型消息前移除 gold 字段。冻结文件不原地修改；新数据需新版本，不能将开发集、修正版与原评分混用。

## 无效首跑与协议修正

[首跑 JSON](results/phase-d-four-arm-provider-run-2026-08-23-invalid.json)和[说明](results/phase-d-four-arm-provider-run-2026-08-23-invalid.md)保留了 240 次调用完成但独立 Reviewer 全接受的退化结果。该运行对质量比较无效，不能把调用成功等同于实验有效。

[协议修正记录](v2-protocol-amendment-2026-08-23.json)保留提示词措辞修正、公平预算不变、退化诊断和证据政策。修正后的 v1 重跑只能算事后回归；新的评分使用新冻结的 v2 数据。v1 规则基线依赖原措辞，在 v2 根因集仅正确 6/24，也不构成模型在一般任务上优越的证明。

## 复现入口

以下命令均在 `backend` 目录运行，供主动复现时使用。当前文档整理未执行这些命令；新运行应记录真实代码 SHA、环境与数据哈希，不能沿用旧快照标签。

### 受控修复与全量检索前后对比

Docker 可用时，`DevMindMySqlIntegrationTest` 从空库迁移、导入固定演示数据，先持久化全部 active chunk 的本地稀疏向量，再执行相同的 40 条检索用例；批准并执行一个预置元数据修复后，重跑同一套用例。

```powershell
$env:DEVMIND_EVAL_KNOWLEDGE_SNAPSHOT='git:<实际待测提交 SHA>'
./mvnw.cmd "-Dtest=DevMindMySqlIntegrationTest" test
```

若 Docker 29 环境报客户端 API 版本过低，历史环境使用过 `-Dapi.version=1.44`；Windows Docker Desktop Linux Engine 可按环境需要设置 `DOCKER_HOST=npipe:////./pipe/dockerDesktopLinuxEngine`。这不是所有环境的必需配置。

结果写到 `target/evaluation/phase-d-controlled-repair-report.json`。普通运行未填写快照时使用 `git:working-tree` 占位。目标修复率只覆盖一个固定样例；生产修复动作自身只做目标问题回归，完整 before/after 由这个独立实验采集。

### 真实模型实验（默认跳过）

Tool Calling smoke 需要同时设置 `DEVMIND_RUN_DEEPSEEK_SMOKE=true` 和 `DEVMIND_DEEPSEEK_API_KEY`，运行 `DeepSeekAgentModelClientSmokeTest`。

四臂运行器 `V2DeepSeekProviderEvaluationTest` 还要求：

- `DEVMIND_RUN_V2_EVALUATION=true`
- `DEVMIND_DEEPSEEK_API_KEY`、精确的 `DEVMIND_DEEPSEEK_MODEL`
- `DEVMIND_EVAL_KNOWLEDGE_SNAPSHOT`
- `DEVMIND_DEEPSEEK_INPUT_USD_PER_MILLION`、`DEVMIND_DEEPSEEK_OUTPUT_USD_PER_MILLION`
- `DEVMIND_EVAL_PRICING_BASIS`：记录价目表来源与估算限制

```powershell
./mvnw.cmd "-Dtest=V2DeepSeekProviderEvaluationTest" test
```

该实验最多尝试 240 次模型调用，请先确认模型可用性与成本。运行器读取 2.0.0 冻结数据、计算原始字节哈希、使用 temperature 0，并把无效响应 / 调用失败按失败案例计入。结果只写入 `target/evaluation/v2-four-arm-provider-report.json`。

向量规模基准默认也跳过，主动运行方法见[检索实验记录](retrieval-results.md)。普通单元测试、历史 smoke 成功以及某次 CI 通过，都不能替代这些独立实验。
