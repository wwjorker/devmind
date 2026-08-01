# DevMind 部署与运维边界

## 当前结论

DevMind 当前是可本地演示、可自动测试、适合 Java / RAG 求职讲解的作品集项目，不应宣称已经达到生产可用。Swagger UI 是后端 API 的开发调试入口，Vue 页面是面向用户的产品入口；两者职责不同，不需要把 Swagger “重做成前端”。

当前已经具备：

- 前后端构建与 GitHub Actions CI；
- Flyway 数据库版本管理；
- JWT、BCrypt、Redis 退出黑名单与接口限流；
- 外部 LLM / embedding / rerank 超时和可插拔 Provider；
- AI 日志、token、耗时、引用、bad case 与检索评估；
- MySQL、Redis、pgvector 的 Compose 数据卷、自动重启和健康检查；
- 默认 Mock + 本地检索的零费用安全启动方式。

## 与生产环境相比仍缺什么

| 领域 | 当前状态 | 生产前需要补齐 |
| --- | --- | --- |
| 应用交付 | Spring Boot、Vite 在本机分别启动 | 后端镜像、前端静态镜像、反向代理与版本化发布 |
| 流量入口 | 本机 `8081/5173` | 域名、HTTPS、证书续期、CORS 与安全响应头 |
| 密钥 | 本地环境变量 / 私有 IDEA 配置 | Secret Manager 或受控密钥注入、轮换与最小权限 |
| 数据库 | 单机 Compose named volume | 托管数据库或高可用、定时备份、恢复演练与保留策略 |
| Redis | 单实例，开发模式可 fail-open | 高可用、认证/TLS、容量和淘汰策略、故障策略评审 |
| 可观测性 | 业务问答日志和本地应用日志 | 结构化日志、指标、trace、告警、仪表盘与请求关联 ID |
| 发布 | CI 只做测试与构建 | 镜像扫描、制品仓库、staging、迁移检查、灰度/回滚 |
| 容量与韧性 | 超时、限流 | 压测、并发预算、连接池、重试/熔断、成本和配额告警 |
| 安全 | 基础认证与数据隔离 | 依赖/SAST/DAST、审计日志、权限模型、渗透测试与应急预案 |

## 推荐实施顺序

### 第一阶段：作品集可部署版

1. 为后端增加多阶段 Dockerfile，为前端增加 Nginx 静态镜像。
2. 新增独立的 deployment Compose，不复用本地开发 Compose。
3. 所有密钥只从部署环境注入，不写入镜像或仓库。
4. 增加 Spring Boot Actuator、Prometheus 指标和结构化日志。
5. 在 CI 中构建镜像并执行容器级 smoke test。

完成后可以表述为“可容器化部署”，仍不要表述为“生产级高可用”。

### 第二阶段：真实生产准备

1. 选择云数据库、Redis 和向量存储方案，制定 RPO/RTO。
2. 配置 HTTPS、网关、密钥管理、监控告警和集中日志。
3. 做备份恢复演练、并发压测、故障注入和回滚演练。
4. 明确 DeepSeek、embedding、rerank 的预算、配额、降级和数据合规边界。

## 当前日常运行约定

- 唯一推荐本地数据环境：Compose MySQL `3307`、Redis `6380`、可选 pgvector `5433`。
- IDEA 日常选择 `DevMind - Local Mock`；真实 Provider 只在明确验证时启用。
- `http://127.0.0.1:5173` 用于人工体验；`http://127.0.0.1:8081/swagger-ui/index.html` 用于查看和调试后端接口。
- 完整检索评估由用户主动触发；远程 embedding/rerank 开启时可能产生等待和费用。
- 不删除 named volume 来解决普通启动问题；先核对端口、容器、JDBC URL 和 Flyway 状态。

## 发布前最低检查清单

- [ ] 后端测试、前端构建、依赖审计全部通过。
- [ ] 仓库与 Git 历史不包含真实 API Key、数据库密码或 JWT secret。
- [ ] Flyway 在空数据库和已有数据库上均完成验证。
- [ ] 备份能够恢复到独立环境，并验证文档、日志和向量重建。
- [ ] 外部 Provider 超时、限流、配额耗尽和降级路径均有演练。
- [ ] 监控能发现 5xx、延迟升高、数据库连接耗尽和 AI 成本异常。
- [ ] 发布制品可追溯到 Git commit，回滚步骤经过演练。
