# DevMind 新手术语表

这份文档用人话解释 DevMind 里常见的工程和 AI 术语。你不用一次背完，先知道它们在项目里解决什么问题。

## Chunk 是什么

Chunk 就是“文档分块”。

如果一篇笔记很长，系统不会把整篇都直接丢给 AI，而是切成几段小文本。每一段就是一个 chunk。

例子：

```text
一篇 Redis 缓存穿透笔记
-> chunk 1: 问题现象
-> chunk 2: 根因
-> chunk 3: 解决方案
```

这样做的原因：

- 大模型上下文有限，不能无限塞资料。
- 检索时粒度更细，更容易找到相关段落。
- 回答时可以引用具体 chunk，方便解释答案来源。

## 检索相关 chunks 是什么

用户提问后，系统先不急着问 AI，而是先从知识库里找相关资料。

例如用户问：

```text
How should I explain Redis cache penetration in an interview?
```

系统会找包含 Redis、cache、penetration 等关键词的 chunk。

这个过程就叫检索。

## 计算分数是什么

分数表示“这个 chunk 和问题有多相关”。

DevMind 当前版本是可解释关键词打分：

- 关键词命中文档内容，加分。
- 关键词命中标题，加更多分。
- 关键词命中 tags，也加分。

分数越高，越可能被放进 Prompt 给 AI 参考。

这不是最终最强算法，但优点是容易调试。你能看到系统为什么召回某个 chunk。

## 上下文是什么

上下文就是“提供给 AI 的参考资料”。

在 DevMind 里，上下文主要来自检索到的 chunks。

没有上下文时，AI 只能靠自己已有知识回答，容易乱编。给它上下文后，它就能基于你的知识库回答。

## 构建带上下文的 Prompt 是什么

Prompt 是发给大模型的完整输入。

普通提问可能只有：

```text
请解释 Redis 缓存穿透
```

DevMind 构建的 Prompt 会更完整：

```text
你是 DevMind，只能根据提供的知识库上下文回答。

问题：
请解释 Redis 缓存穿透

检索到的上下文：
[chunkId=3] Redis 缓存穿透是...
[chunkId=4] 解决方案包括缓存空值...
```

这就是“带上下文的 Prompt”。

## Mock 是什么

Mock 是“假的实现”，用于本地测试。

Mock LLM 不会真的调用 DeepSeek，也不会花 API 费用。它会根据输入返回一段固定或模拟的答案。

作用：

- 没有 API Key 也能跑通项目。
- 写测试时结果更稳定。
- 调试后端流程时不浪费 Token。

## DeepSeek Provider 是什么

Provider 可以理解成“大模型服务提供方”。

DeepSeek Provider 就是调用 DeepSeek API 的实现。

如果配置：

```text
DEVMIND_AI_PROVIDER=deepseek
```

系统就会走 DeepSeek。如果配置成 mock，就走本地 Mock。

## LLM Provider 抽象是什么意思

LLM 是 Large Language Model，大语言模型。

Provider 抽象的意思是：业务代码不直接写死“只能调用 DeepSeek”，而是先定义一个统一接口 `LlmClient`。

项目里有：

```text
LlmClient 接口
-> MockLlmClient
-> DeepSeekLlmClient
```

业务服务只认识 `LlmClient`，不关心背后是真模型还是 Mock。

好处：

- 本地测试可以用 Mock。
- 真实演示可以用 DeepSeek。
- 以后接通义千问、OpenAI、Claude API 时，不用大改业务代码。
- 避免项目和某一个模型厂商强绑定。

## Router 是什么

Router 是“路由器”，负责选择该用哪个 Provider。

例如配置里写：

```text
DEVMIND_AI_PROVIDER=deepseek
```

Router 就找到支持 deepseek 的 `DeepSeekLlmClient`。

如果配置是 mock，就找到 `MockLlmClient`。

## 软删除是什么

软删除不是把数据库记录真的删掉，而是把状态改成“已删除”或“已归档”。

例如：

```text
status = 1 active
status = 0 archived
```

用户看不到归档数据，但数据库里还保留。

好处：

- 防止误删。
- 可以恢复。
- 可以审计历史记录。

很多企业系统都会对重要业务数据使用软删除。

## Flyway Migration 是什么

Flyway 是数据库版本管理工具。

Migration 是“迁移脚本”，也就是数据库结构变更脚本。

以前你要手动在 DBeaver 执行 SQL 建表。用了 Flyway 后，脚本放在项目里：

```text
V1__init_schema.sql
V2__add_xxx.sql
V3__create_xxx.sql
```

应用启动时，Flyway 会自动检查哪些脚本执行过，哪些没执行，然后只执行新的。

企业项目里经常用 Flyway 或 Liquibase 管理数据库变更。

## Redis JWT Logout 黑名单是什么

JWT 是无状态 Token。服务端默认不保存它。

问题是：用户点退出登录后，如果这个 JWT 还没过期，理论上它仍然可能被继续使用。

DevMind 的做法是：

```text
用户 logout
-> 后端把这个 token 的 hash 存进 Redis
-> 设置 TTL，等 token 原本过期时自动删除
-> 之后请求进来，认证过滤器先查 Redis 黑名单
-> 如果在黑名单里，就拒绝
```

这是常见工程做法之一。也有企业会用更完整的会话管理、短 Token + Refresh Token、网关统一鉴权等方案。

## TTL 是什么

TTL 是 Time To Live，存活时间。

例如把 Token 放进 Redis 30 分钟，30 分钟后 Redis 自动删除它。

## Spring Security 是什么

Spring Security 是 Spring 体系里的安全框架。

它负责：

- 登录认证
- 接口权限控制
- 解析当前用户
- 处理未登录和无权限请求
- 接入 JWT 过滤器

## BCrypt 密码加密是什么

用户密码不能明文存数据库。

BCrypt 是一种密码哈希算法。注册时把密码变成不可逆的 hash，登录时再用 BCrypt 校验。

数据库里存的是 hash，不是原密码。

## Vite 是什么

Vite 是前端开发和构建工具。

它负责：

- 启动前端本地服务
- 热更新
- 打包生产文件

你运行：

```text
npm run dev
```

背后就是 Vite 在启动前端。

## TypeScript 是什么

TypeScript 是带类型的 JavaScript。

它能提前发现很多错误，例如：

- 字段名写错。
- 接口返回数据类型不对。
- 函数参数类型不对。

Vue 3 项目里常用 TypeScript 提高可维护性。

## GitHub Actions 是什么

GitHub Actions 是 GitHub 自带的自动化工具。

DevMind 里它会在 push 后自动执行：

```text
backend: mvn test
frontend: npm ci && npm run build
```

这叫 CI，持续集成。

作用是防止你提交了跑不起来的代码。

## 创建知识文档只能手动输入吗

当前版本是手动输入文本。

这是 v1 的限制，不是最终形态。这样做是为了先跑通核心链路：

```text
文本资料 -> chunk -> 检索 -> Prompt -> AI 回答 -> 日志和反馈
```

后续可以升级：

- Markdown 文件导入
- PDF 文本解析
- 批量导入笔记
- 从 GitHub README 或项目文档同步
- 图片 OCR

面试时可以诚实说：第一版先支持文本创建，后续计划做 Markdown/PDF 导入，提高知识入库效率。

## Evaluation 是什么

Evaluation 是评估。

它不是“模型训练”，而是用来回答：

```text
系统答得好吗？
哪些问题答不好？
为什么没召回？
哪些 bad case 需要优化？
```

DevMind 当前 Evaluation 是轻量版：

- 统计 helpful / bad case。
- 展示最近 bad case。
- 展示 ask logs。

后续可以升级为：

- 标准问题集。
- 标准答案。
- Hit Rate。
- MRR。
- RAGAS。
- 检索召回评估。
