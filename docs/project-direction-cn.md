# DevMind 项目方向护栏

这份文档用来防止项目越做越散。后续新增功能、改 UI、写 README、写简历时，优先按这里的规则判断。

## 1. 项目定位

DevMind 是一个面向国内 Java 后端求职的 AI 知识库项目。

它不是通用知识库产品，也不是炫技型 Agent 项目。核心价值是把 AI 问答能力接入标准后端工程链路：

```text
认证登录
-> 知识文档管理
-> 文档分块 chunk
-> 关键词检索
-> Prompt 构建
-> LLM Provider 路由
-> 引用来源 citations
-> 问答日志 ask log
-> token / 耗时观测
-> bad case 反馈
-> RAG 评估集
```

面试时要强调：AI 是系统中的一条业务链路，不是单独调用一个聊天接口。

## 2. 语言规则

面向国内求职展示，公开材料采用中文优先。

- 前端页面：中文为主，保留必要英文技术词。
- 根目录 `README.md`：中文为主，给面试官快速理解项目。
- 简历与面试文档：中文为主。
- 代码类名、方法名、接口路径、数据库字段：继续使用英文。
- 技术关键词：保留英文或中英混写，例如 `RAG`、`Prompt`、`chunk`、`token`、`Provider`、`Flyway`、`Redis blacklist`。
- 本地学习笔记：中文，优先放在本地私有目录，不必全部提交 GitHub。

如果出现“中文页面里有英文”，先判断它是技术关键词、历史测试数据还是 UI 文案。技术关键词可以保留；UI 文案应尽量中文化。

## 3. 功能取舍原则

优先做能在 Java 后端面试中讲清楚的功能。

高优先级：

- 用户认证、数据隔离、权限控制。
- 文档分块、检索、Prompt 构建、引用来源。
- LLM Provider 抽象、Mock/真实模型切换。
- 问答日志、token 成本、耗时、失败兜底。
- bad case 反馈和 RAG 评估集。
- CI、README、运行说明、面试讲解文档。

低优先级：

- 炫技多 Agent。
- 复杂动画和营销页。
- 没有明确面试讲点的大而全功能。
- 为了“像产品”而加入大量和后端能力无关的页面。

## 4. 成熟库复用原则

基础设施和通用能力优先复用成熟生态：

- 后端框架：Spring Boot、Spring Security、MyBatis-Plus、Flyway。
- 前端框架：Vue、Vite、TypeScript。
- 后续文档解析：可以考虑 Apache Tika、PDFBox。
- 后续检索升级：可以考虑 Lucene、Elasticsearch/OpenSearch、向量数据库或 rerank 服务。

不要直接复制 GitHub 上的完整项目作为主线。简历含金量来自自己能解释的设计决策，而不是代码量。

## 5. 审查节奏

每完成 2 到 3 个实质功能，做一次项目审查：

1. 这个功能能不能写进简历？
2. 面试官追问时能不能讲清楚代码和取舍？
3. 是否增加了后端工程深度？
4. 是否让项目更像完整系统，而不是更像 demo？
5. README、面试文档和测试是否同步更新？

Claude Code 可以作为阶段性审查员，但最终取舍按这份方向护栏判断。

## 6. 当前版本边界

v1 目标是完成可演示、可讲解、可写简历的主链路：

- 文档管理与导入。
- chunk 生成与检索。
- AI 问答与引用来源。
- Mock/DeepSeek Provider 切换。
- Ask Log、Prompt Preview、token 统计。
- bad case 反馈。
- RAG evaluation dataset 覆盖率。
- 前端可视化演示。

v2 再考虑：

- 关键词 + 向量混合检索。
- rerank。
- PDF/OCR 导入。
- 更正式的离线评估指标，例如 hit rate、MRR。
- 更细粒度的权限和限流。

