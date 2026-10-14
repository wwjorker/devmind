# DevMind 后端

Spring Boot 后端负责认证、文档管理、RAG 问答、反馈评估及经人工审批的元数据修复。设计与事务边界集中在[架构说明](docs/architecture.md)，实验集中在[评估说明](evaluation/README.md)。

## 本地运行

需要 JDK 17+ 与 Docker Desktop。以下命令从仓库根目录执行：

```powershell
./backend/scripts/run-local.ps1
```

脚本启动 Compose 中的 MySQL `3307`、Redis `6380`，使用 Maven Wrapper 构建后在 `8081` 端口运行后端。它会继承已有环境变量，没有配置时使用 mock、本地稀疏向量和 MySQL JSON 存储。

使用 IDEA 时，先在仓库根目录启动依赖：

```powershell
docker compose -f backend/docker-compose.yml up -d devmind-mysql devmind-redis
```

再用 IDEA 打开 `backend` 并选择共享运行配置 `DevMind - Local Mock`，运行 `com.devmind.DevMindApplication`。共享配置显式设置本地 provider；远程模型密钥只放在个人运行配置或进程环境中。

Flyway 自动执行 [数据库迁移](src/main/resources/db/migration)。新环境不需要手动建表；已有旧库的 baseline 配置不能替代迁移兼容性检查。演示 SQL 是可选的数据重置操作，使用前阅读[本地体验指南](../docs/guides/local-demo.md)。

## 模型与检索配置

完整变量示例见 [.env.example](.env.example)，实际默认值见 [application.yml](src/main/resources/application.yml)。**应用和启动脚本不会自动加载后端 `.env` 文件**，请将变量注入运行进程或 IDEA 配置。默认数据库为 `devmind`，Compose 本地凭据为 `root/root`；Redis 使用 database `1`。

| 能力 | 默认值 | 启用方式 |
| --- | --- | --- |
| 问答模型 | `DEVMIND_AI_PROVIDER=mock` | 改为 `deepseek`，并配置下述 DeepSeek 变量。 |
| 向量表示 | `DEVMIND_AI_EMBEDDING_PROVIDER=local-sparse-vector` | 改为 `remote-dense`，配置远程 embedding。 |
| 重排 | `DEVMIND_AI_RERANK_PROVIDER=none` | 改为 `remote-rerank`，仅用于离线检索评估。 |
| 向量查询 | `DEVMIND_VECTOR_STORE_PROVIDER=mysql-json` | 配置 dense embedding 后可选 `pgvector`。 |

启用真实服务前，请明确选择 provider；仅填写 Key 不会把普通 mock 问答切换成真实生成。反过来，**Triage / Reviewer 动作直接使用 DeepSeek Agent 客户端**，不受普通问答 mock 开关保护；没有 Key 时不能运行，配置 Key 后点击这些动作会产生真实调用。预置提案的审批与执行无需重新生成诊断。

### DeepSeek

在后端运行环境中设置（以下是变量格式示例，不是 PowerShell 赋值命令）：

```dotenv
DEVMIND_AI_PROVIDER=deepseek
DEVMIND_DEEPSEEK_API_KEY=<你的密钥>
DEVMIND_DEEPSEEK_BASE_URL=https://api.deepseek.com
DEVMIND_DEEPSEEK_MODEL=<账号可用的模型标识>
DEVMIND_DEEPSEEK_TEMPERATURE=0.2
```

仓库默认模型标识为 `deepseek-v4-flash`，这里仅说明现有配置，不保证供应商当前仍提供该型号。Agent 路径需要模型支持项目使用的 Tool Calling 协议。普通问答调用失败时会记录失败日志并降级为 mock，响应 provider 链用于区分降级回答。

### 远程 embedding 与 rerank

```dotenv
DEVMIND_AI_EMBEDDING_PROVIDER=remote-dense
DEVMIND_AI_EMBEDDING_REMOTE_BASE_URL=<兼容服务的 API 根地址>
DEVMIND_AI_EMBEDDING_REMOTE_API_KEY=<你的密钥>
DEVMIND_AI_EMBEDDING_REMOTE_MODEL=<embedding 模型标识>
DEVMIND_AI_EMBEDDING_REMOTE_DIMENSION=1024

DEVMIND_AI_RERANK_PROVIDER=remote-rerank
DEVMIND_AI_RERANK_REMOTE_BASE_URL=<rerank 服务的 API 根地址>
DEVMIND_AI_RERANK_REMOTE_API_KEY=<你的密钥>
DEVMIND_AI_RERANK_REMOTE_MODEL=<rerank 模型标识>
```

客户端分别在根地址后拼接 `/embeddings` 与 `/rerank`；需要 `/v1` 的服务应在根地址中包含它。embedding 维度必须由所选模型支持。文档写入、向量回填和检索评估可能调用这些服务；切换 provider 后，已有文档可能需要通过 [API 示例](docs/api/devmind-api.http)中的回填接口建立对应向量。

### 可选 pgvector

从仓库根目录启动向量数据库：

```powershell
docker compose -f backend/docker-compose.yml up -d devmind-pgvector
```

然后配置后端：

```dotenv
DEVMIND_VECTOR_STORE_PROVIDER=pgvector
DEVMIND_PGVECTOR_URL=jdbc:postgresql://localhost:5433/devmind_vectors
DEVMIND_PGVECTOR_USERNAME=devmind
DEVMIND_PGVECTOR_PASSWORD=devmind
DEVMIND_PGVECTOR_DIMENSION=1024
DEVMIND_AI_EMBEDDING_REMOTE_DIMENSION=1024
```

这需要同时启用 `remote-dense`。MySQL JSON 保存向量源数据，pgvector 提供 HNSW 查询索引，两端维度必须一致。回填可重放已有的 active dense JSON，也可能为缺失向量调用 embedding。改变维度涉及重新生成向量和索引，不能把旧向量重放误当作维度转换。

### 常用运行参数

| 参数 | 默认值与用途 |
| --- | --- |
| `DEVMIND_DB_URL` | `localhost:3307/devmind` 的 JDBC URL，完整格式见 `.env.example`。 |
| `DEVMIND_REDIS_HOST` / `DEVMIND_REDIS_PORT` | `localhost` / `6380`。 |
| `DEVMIND_JWT_SECRET` | 仓库默认仅供本地开发，共享环境需覆盖。 |
| `DEVMIND_BLACKLIST_FAIL_OPEN` | `true`；Redis 故障时放行，登出撤销保护会受影响。 |
| `DEVMIND_AI_ASK_RATE_LIMIT_PER_MINUTE` | `10`；按用户限制 AI 问答次数。 |
| `DEVMIND_AI_ASK_RATE_LIMIT_FAIL_OPEN` | `true`；关闭后限流服务故障返回 503。 |
| `DEVMIND_RETRIEVAL_EVALUATION_COOLDOWN_SECONDS` | `300`；完整检索评估冷却时间。 |
| `DEVMIND_AI_CONNECT_TIMEOUT` / `DEVMIND_AI_READ_TIMEOUT` | `5s` / `30s`；外部 HTTP 调用超时。 |
| `DEVMIND_RETRIEVAL_TECH_PHRASES_ENABLED` | `true`；关闭可做技术短语表消融。 |

其余开关见 `application.yml`。部署前的凭据、数据卷和故障策略见[维护说明](../docs/operations/production-readiness.md)。

## API 与日志

后端启动后访问 <http://localhost:8081/swagger-ui.html>，或使用 [IDEA HTTP Client 示例](docs/api/devmind-api.http)。接口覆盖认证、文档、检索、问答日志、反馈、评估和修复审批；先注册 / 登录获取 token。

问答日志保存 provider、召回片段 ID、Prompt 预览、token 用量、耗时与成功 / 失败状态。完整模型输入与最多 2,000 字符的日志预览分离；新日志保存模型可见的有界证据快照及文档版本。反馈覆盖率、检索指标与生成质量是不同指标，定义见[评估说明](evaluation/README.md)。

## 测试与 CI

在 `backend` 目录执行：

```powershell
./mvnw.cmd test
```

测试包含单元测试和 Testcontainers 集成测试；Docker 不可用时相关集成测试可能跳过。真实 DeepSeek smoke、四臂模型评估和规模基准均为显式开启的 opt-in 项，启用条件见[评估说明](evaluation/README.md)。普通测试通过不等于这些外部实验已运行。

[GitHub Actions](../.github/workflows/ci.yml)负责后端测试与前端构建；实际结果以对应提交的运行记录为准。
