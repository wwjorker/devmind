# DevMind 项目交接记忆

这份文档用于长对话压缩、重开对话或切换 AI 时快速恢复上下文。它不是简历材料，也不是给面试官直接看的公开介绍。

## 项目定位

DevMind 是一个面向 Java 后端求职的 AI 知识库项目。它不是单纯调用大模型接口的 demo，而是把 RAG 问答接入一个完整后端系统：

```text
用户登录
-> 知识文档管理
-> 文档分块 chunk
-> 多关键词检索和打分
-> 构建带上下文的 Prompt
-> 路由到 Mock 或 DeepSeek Provider
-> 返回答案和引用来源
-> 记录问答日志、token、耗时、召回 chunk
-> bad case 反馈
-> RAG 评估集覆盖率
```

目标岗位是中国求职场景下的 Java 后端开发岗，不是实习生项目。项目展示重点应放在后端工程能力和 AI 应用工程化，而不是炫技。

## 当前仓库结构

```text
F:\AI项目\devmind
├── backend   Spring Boot 后端
├── frontend  Vue 3 + Vite + TypeScript 前端
└── docs      演示脚本、学习笔记、项目说明
```

不要修改或移动：

```text
F:\cangqiong
```

这是另一个苍穹外卖项目，DevMind 不应影响它。

## 当前技术栈

后端：

- Java 17，当前 IDEA 使用 `F:\IntelliJ IDEA 2024.1.2\jbr`
- Spring Boot 3
- Spring Security + JWT
- BCrypt 密码加密
- MyBatis-Plus
- MySQL
- Redis，用于 JWT logout blacklist
- Flyway migration
- DeepSeek API + Mock Provider
- Springdoc Swagger

前端：

- Vue 3
- Vite
- TypeScript
- 中文优先界面，保留 RAG、Prompt、chunk、token、Provider 等技术词

## 已完成能力

- 用户注册、登录、JWT 鉴权
- 用户级知识文档隔离
- 文档创建、更新、软删除
- Markdown/TXT 文件导入
- 自动生成 knowledge_document_chunk
- 中英文混合关键词抽取
- chunk 检索、标题/标签/内容打分
- 重复 chunk 降权
- Prompt Preview
- Mock/DeepSeek Provider 路由
- 无上下文兜底，避免胡编
- AI 问答日志
- token 用量记录
- bad case 反馈
- RAG 评估集覆盖率
- 前端可视化演示界面

## 当前重要文件

演示脚本：

```text
F:\AI项目\devmind\docs\demo\devmind-demo-script-cn.md
```

演示数据重置脚本：

```text
F:\AI项目\devmind\backend\docs\sql\reset-and-seed-demo-data-for-testuser.sql
```

样例知识文档：

```text
F:\AI项目\devmind\backend\docs\samples\redis-cache-penetration.md
F:\AI项目\devmind\backend\docs\samples\jwt-logout-redis-blacklist.md
F:\AI项目\devmind\backend\docs\samples\flyway-migration.md
F:\AI项目\devmind\backend\docs\samples\llm-provider-abstraction.md
F:\AI项目\devmind\backend\docs\samples\no-context-fallback.md
F:\AI项目\devmind\backend\docs\samples\rag-quality-evaluation.md
```

面试讲解文档：

```text
F:\AI项目\devmind\backend\docs\interview-guide-cn.md
```

## 本地环境注意事项

- IDEA 项目 SDK 建议使用 Java 17。当前可用的是 IDEA 自带 JBR：`F:\IntelliJ IDEA 2024.1.2\jbr`
- Windows 终端里的 `java -version` 仍可能是 Java 8，这不代表 IDEA 启动后端失败。
- 不要全局修改 Java 环境变量，避免影响苍穹外卖等其他项目。
- DeepSeek API Key 可以先放在本地 IDEA 运行配置的环境变量里，但不要提交 `.idea`。
- 正式投简历或公开演示前，建议重置 DeepSeek API Key。

## 演示数据脚本说明

`reset-and-seed-demo-data-for-testuser.sql` 用于清理并重建 `testuser` 的 DevMind 演示数据。

它会删除并重建：

- `testuser` 的知识文档
- `testuser` 的 chunks
- `testuser` 的问答日志
- `testuser` 的 bad case 反馈

它不会：

- 删除 `testuser` 账号
- 删除其他用户数据
- 修改 `sky_take_out`
- 修改 `F:\cangqiong`

如果 DBeaver 执行时报 `Illegal mix of collations`，脚本开头需要有：

```sql
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET @demo_username := _utf8mb4'testuser' COLLATE utf8mb4_unicode_ci;
```

如果 DBeaver 里中文显示乱码，不要执行。应使用 UTF-8 打开，或从 IDEA 里打开 SQL 文件后复制到 DBeaver 新 SQL 编辑器执行。

## 下一阶段优先级

1. 修好并执行演示数据脚本，让本地演示数据干净。
2. 提交并推送当前文档、样例和脚本。
3. 让 GitHub Actions 跑通。
4. 补 README 截图和中文演示说明。
5. 做一次 Claude Code 审查，重点查安全、简历可信度、代码可讲性。
6. 再考虑更高级功能：检索评估指标、向量检索、rerank、更多真实笔记导入。

## 面试表达主线

不要说“我做了一个 AI 问答网站”。应该说：

```text
我做的是一个面向个人开发学习资料的 AI 知识库系统。
核心不是简单调用大模型，而是围绕 RAG 做了一套后端工程链路：
文档入库、自动分块、关键词召回、Prompt 构建、模型 Provider 抽象、
引用来源、token 和耗时日志、bad case 反馈，以及评估集覆盖率。
```

重点讲：

- 为什么要 chunk
- 检索怎么打分
- 为什么无上下文时不能胡编
- 为什么抽象 LlmClient
- Redis logout blacklist 解决什么问题
- Flyway migration 解决什么问题
- bad case 和评估集如何帮助持续优化 RAG

