# 检索实验与向量查询基准

## 记录来源与适用范围

本文保留整理前 `213dad9` 版本根 README 与后端 README 中已有的实验数据。它们是历史记录，本次文档整理没有重新运行实验。

五策略对比、词表消融和规模基准的原文未提供完整运行日期、精确代码快照及独立原始结果文件，因此保留数字作为参考，不能当作当前版本验收或生产性能承诺。可核验的另一组实验是 [2026-08-23 受控修复前后对比](results/phase-d-controlled-repair-run-2026-08-23.md)，它有独立 JSON、知识快照和数据哈希；其中 sparse MRR 为 0.9381，与下表的 0.924 属于不同记录，不应混算。

## 方法与指标

检索集位于 [v1-retrieval-cases.json](../src/main/resources/evaluation/v1-retrieval-cases.json)，相关性由人工标注的文档标题判定，关键词命中只用于诊断。Hit@3 表示前 3 个结果是否包含相关文档；MRR 为首个相关结果排名倒数的平均值。35 个正例计算检索指标，5 个库外负例单独检查是否空召回；这些指标不衡量生成答案质量。

本地可通过页面评估看板或 [API 请求示例](../docs/api/devmind-api.http)运行检索评估。默认仅有本地策略；外部策略需要对应服务与配置，并可能产生费用，见[后端配置](../README.md#模型与检索配置)。本文不自动执行任何实验。

## 历史五策略对比

在 14 篇文档、40 个人工标注 gold-label 用例（35 个正例含词法失配、同义改写、跨主题 hard-negative，5 个库外负例）上，对五种检索策略计算 Hit@3 / MRR（K=3，ngram FULLTEXT + 字面证据过滤生效后测得；dense 向量为 1024 维，以适配 pgvector HNSW 的 2000 维上限）：

| 策略 | Hit@3 | MRR | 特点 |
|---|---|---|---|
| keyword baseline | 0.857 | 0.843 | 字面证据优先，精确但漏改写问题 |
| sparse-hybrid | **1.000** | 0.924 | 向量通道补齐改写召回 |
| dense-hybrid | 0.971 | 0.920 | dense 向量 + JVM 暴力余弦（对照组） |
| dense-hybrid-pgvector | 0.971 | **0.934** | 同批向量改由 pgvector HNSW serving |
| dense-hybrid-rerank | **1.000** | 0.852 | 这批正例均命中 top-3，平均排名倒数较低 |

在这批样例中，Hit@3 与 MRR 没有由同一策略同时取得最优值；rerank 的 Hit@3 较高，MRR 较低。这不能推出 rerank 能保证任意问题命中，也不能推出 dense embedding 普遍优于稀疏检索。**在本次 35 个正例评估中，pgvector HNSW 与同批向量的 JVM 暴力余弦 Hit@3 均为 0.971**；原记录将 MRR 的微小差异解释为排序精度差异：JVM 路径把相似度圆整为整数分会产生并列，pg 按原始距离排序。注意 rerank 是在混合检索 top-5 候选池上做"5 选 3"重排，其 Hit@3 满分受候选池限制；Hit@3 相邻两档相差约 0.029，以上数字为方向性而非统计显著。

**词表消融**：问题解析里有一张人工技术短语表，为验证评估不依赖这张表，用 `DEVMIND_RETRIEVAL_TECH_PHRASES_ENABLED=false` 关掉后重跑：

| 策略 | 词表开 | 词表关 |
|---|---|---|
| keyword baseline | 0.857 / 0.843 | 0.714 / 0.686 |
| sparse-hybrid | 1.000 / 0.924 | 1.000 / 0.886 |

在这批用例中，关闭短语表后 keyword baseline 的 Hit@3 下降约 0.14；sparse-hybrid 的 Hit@3 仍为 1.000，但 MRR 从 0.924 降至 0.886。

**负例与已知限制**：5 个库外问题中 1 个正确触发空召回兜底，其余 4 个仍被本地稀疏向量通道以 0.16–0.23 的弱相似度召回。校准数据显示，改写正例的 gold 相似度（约 0.14）低于 hard-negative 的噪声相似度（约 0.16），在 bigram 稀疏表示下两者发生倒挂，不存在能分开它们的阈值。这说明这批样例中单靠稀疏相似度阈值不能区分正负例；当前在线无上下文判定仍依赖空召回，dense 阈值方案尚未接入。

## 历史向量查询规模基准

合成聚簇语料（64 簇 + 受控噪声，模拟真实 embedding 的流形结构）、1024 维、单机实测（i7-12700H / 16GB / Windows 11，Postgres 运行于 Docker Desktop WSL2），每组含预热、top-10、50 次查询：

实现见 [PgVectorBenchmarkTest](../src/test/java/com/devmind/module/search/vectorstore/PgVectorBenchmarkTest.java)。暴力余弦一侧只测内存计算，**不包含 MySQL 查询和 JSON 解码**；HNSW 一侧通过 JDBC 查询。因此这不是两个完整在线接口的端到端延迟对比，50 次查询的 P99 也不宜视为稳定容量指标。

| 规模 | 暴力余弦 P50 / P99 | HNSW ef=40（默认）P50 / P99 / recall@10 | HNSW ef=120 P50 / P99 / recall@10 |
|---|---|---|---|
| 1 万 | 6.0 / 13.2 ms | 1.6 / 2.0 ms / **1.000** | 1.7 / 4.0 ms / 1.000 |
| 10 万 | 62.7 / 94.1 ms | 4.5 / 35.1 ms / **0.768** | 4.9 / 7.4 ms / **0.952** |

这次记录中的观察：

1. 数据量从 1 万增至 10 万时，暴力余弦 P50 从 6.0 ms 增至 62.7 ms，默认 HNSW 从 1.6 ms 增至 4.5 ms。这是两个规模点的观察，不代表完整的扩展性曲线。
2. 默认 `ef_search=40` 时，recall@10 从 1 万条的 1.000 降至 10 万条的 0.768，显示这批数据上的近似检索损失。
3. 在 10 万条数据上，把 `ef_search` 从 40 调到 120 后，recall@10 从 0.768 升至 0.952，查询 P50 从 4.5 ms 增至 4.9 ms。P99 在本次测量中更低，但只有 50 次查询，不能据此断言延迟更稳定。

`ef_search` 控制查询时的动态候选列表大小，提高它通常以更多查询工作换取更高召回，见 [pgvector 查询参数说明](https://github.com/pgvector/pgvector#query-options)。它不控制索引构建；建图相关参数包括 `m` 和 `ef_construction`，见[索引参数说明](https://github.com/pgvector/pgvector#index-options)。

另一个独立记录是：10 万条数据的 HNSW 建索引耗时约 4.6 分钟。测试先构建索引并记录耗时，再切换 `ef_search` 测量查询，因此不能把这段建索引时间归因于提高 `ef_search`。

限制：合成聚簇数据比真实 embedding 分布更"干净"，recall 数字偏乐观；纯随机（无簇结构）高维向量下 HNSW recall 会塌到 ~0.26，该结果同样仅为旧文档记录，缺少独立原始报告，不能据此概括所有高维数据。可选复现命令（在 `backend` 目录执行；需要 Docker）：

```bash
./mvnw test -Dtest=PgVectorBenchmarkTest -Ddevmind.benchmark=true \
  -Ddevmind.benchmark.vectorCount=100000
```
