# DevMind 实时学习笔记

## 50 为什么 Stage B 先做可选 remote dense embedding 骨架

本轮只引入 `EmbeddingClientRouter` 和 `RemoteDenseEmbeddingClient` 骨架，默认 provider 仍然是 `local-sparse-vector`。为什么：真实外部 embedding 会带来费用、网络失败和回填复杂度，先把 provider 选择边界拆出来，可以保证默认路径和已有 RRF 混合检索完全不变。面试可能追问：如果线上切 provider 怎么避免误调用外部 API？回答是配置默认本地，remote client 在 api-key 为空时直接抛 `external embedding provider is not configured`，不会发 HTTP。

新增第二个 `EmbeddingClient` 后，`ChunkVectorService` 和 `HybridRetrievalStrategy` 不再裸注入单个接口，而是依赖 `EmbeddingClientRouter`。为什么：Spring 容器里会同时存在 local 和 remote 两个实现，裸按类型注入会产生歧义；router 把选择逻辑集中到一个组件里，也保留了“业务代码只关心 embed/cosine”的调用方式。面试可能追问：为什么不用 `@Primary`？回答是 `@Primary` 会把默认选择藏在 Bean 优先级里，router 更显式，后续做评估对比或按配置切换更清楚。

`RemoteDenseEmbeddingClient.providerName()` 固定返回 `remote-dense`，不包含 model 名。为什么：provider_name 是向量归档和评估分组的稳定维度，同一 remote provider 换模型不应该产生一堆 provider_name，否则后续回填、归档和对比会复杂化；模型名应该放在配置和日志里。面试可能追问：那怎么区分不同模型效果？回答是评估报告可以记录 model 配置，但存储 provider 先保持稳定，真正的模型版本维度后续再单独设计。

本轮不改 `EmbeddingClient` 的 `Map<String, Double>` 签名，dense 向量后续会临时用 `"0".."n-1"` 作为 key 复用现有 JSON 存储和 cosineSimilarity。为什么：这是 Stage B 过渡方案，能最小化对现有稀疏向量、RRF、评估链路的冲击；它不是最终向量存储设计，也不是向量数据库。面试可能追问：什么时候要换接口或 pgvector？回答是当 dense embedding 真正进入回填、查询和规模化检索时，再独立设计维度校验、批量回填、向量索引和数据库方案。

## 51 为什么 remote dense embedding 先接 OpenAI 兼容接口

本轮 `RemoteDenseEmbeddingClient` 使用 OpenAI 兼容的 `POST /embeddings` 协议：`model` 和 `input` 放 JSON body，`Authorization: Bearer <api-key>` 放 header。为什么：硅基流动、DashScope 兼容模式等便宜 embedding 服务大多支持这个格式，代码可以保持 provider 无关；面试可能追问：如果某家 API 字段不兼容怎么办？回答是先稳定通用协议，后续再按 provider 增加 adapter，而不是把厂商分支写进检索主链路。

remote embedding 的 HTTP 客户端用可注入的 `RestClient.Builder`，测试用 `MockRestServiceServer` 拦截请求。为什么：CI 没有真实 key，也不能因为单测产生费用；面试可能追问：怎么证明测试没联网？回答是测试请求被 mock server 接管，并断言 URL、header 和 body，未配置 key 的路径会在发请求前抛异常。

返回的 dense float 数组仍然转成 `Map<String, Double>`，key 是 `"0".."n-1"`。为什么：这是为了兼容现有 `EmbeddingClient` 接口、JSON 存储和 cosineSimilarity 的过渡表示；面试可能追问：这是不是最终向量设计？回答是不是，真正的向量维度、索引和 pgvector 会在后续独立任务里处理。

`dimension` 配置只做 warn，不在本轮强制报错。为什么：不同供应商或模型切换时，先记录维度异常可以帮助定位配置问题，同时避免任务二改变检索链路语义；面试可能追问：生产上会不会应该拦截？回答是回填和持久化阶段应该强校验，否则不同维度的向量混存会污染召回结果。

embedding endpoint 用 `base-url` 去掉末尾 `/` 后显式拼 `/embeddings`，而不是依赖 `RestClient.uri("/embeddings")`。为什么：很多兼容 API 的 base-url 会配置成 `/v1`，显式拼接可以保证最终请求是 `/v1/embeddings`；面试可能追问：怎么证明不会丢 base path？回答是单测用 `MockRestServiceServer` 断言完整 URL 为 `https://embedding.example/v1/embeddings`。

## 52 为什么 sparse 和 dense 向量要按 provider 共存

本轮让同一个 active chunk 可以同时拥有 `local-sparse-vector` 和 `remote-dense` 两套向量，依靠 `knowledge_document_chunk_vector.provider_name` 和 `uk_chunk_provider(chunk_id, provider_name)` 区分。为什么：后续要用同一批 gold-label case 对比 keyword baseline、本地 sparse hybrid、dense hybrid，必须先让两套向量能并存；面试可能追问：为什么不直接覆盖原向量？回答是覆盖会丢掉 baseline，无法做同数据集、同 chunk 粒度的公平对比。

文档内容重建时，旧 chunk 的所有 provider 向量都会一起归档，但新 chunk 只会按当前 configured provider 立即重算。为什么：内容变了以后旧向量全都失效，不论 sparse 还是 dense 都不能继续参与召回；但默认路径不能因为文档更新而偷偷调用外部 dense API。面试可能追问：那 remote-dense 新向量什么时候补？回答是通过显式 `backfillVectors(userId, "remote-dense")` 回填，配置不完整时 fail-fast，不会在没 key 时联网。

回填逻辑只为缺失 active 向量的 chunk 生成向量，已有 active 向量会跳过。为什么：回填要幂等，重复执行不应该重复计费或撞唯一键；面试可能追问：如果同 chunk/provider 只有 archived 记录怎么办？回答是代码会复用这条记录并更新为 active，尊重 `uk_chunk_provider`，避免重复插入。

## 53 为什么混合检索要支持显式 embedding provider

本轮给 `HybridRetrievalStrategy` 增加了 `retrieveWithEmbeddingProvider`，可以指定 `local-sparse-vector` 或 `remote-dense` 来跑同一套关键词召回 + 向量臂 + RRF 融合。为什么：后续要做 keyword baseline / sparse hybrid / dense hybrid 三方对比，必须能在同一语料上切换向量 provider，而不是只依赖全局配置；面试可能追问：默认问答会不会受影响？回答是默认 `retrieve()` 仍走 configured provider，AiAskService 没接新入口。

显式 provider 检索只读取该 provider 的持久化向量，并用同一个 provider 生成 query 向量。为什么：dense hybrid 对比应该基于已回填的 dense chunk vectors，不能在检索时临时给大量 chunk 调外部 embedding；面试可能追问：没有持久化向量怎么办？回答是向量臂返回空，只保留关键词臂结果，需要先跑 provider backfill。

默认 `retrieve()` 保留原来的本地 sparse on-the-fly fallback。为什么：这是已有默认行为，不能因为增加 dense 对比能力影响当前问答链路；面试可能追问：显式 local 和默认 local 是否一致？回答是当使用同一 provider 且已有同一批持久化向量时，测试验证两条路径结果一致。

这份笔记用于记录我们一边开发 DevMind，一边需要真正理解的后端和 AI 应用知识点。

## 01 为什么 documentId 从 1 变成 2

数据库表里的主键 `id` 通常会设置成自增，也就是：

```sql
id BIGINT PRIMARY KEY AUTO_INCREMENT
```

这表示每插入一条新数据，数据库会自动给它分配一个新的编号。

你第一次创建文档时，数据库生成了：

```text
documentId = 1
```

后来你点了：

```text
DELETE /api/v1/documents/1
```

但我们项目里的删除不是物理删除，而是归档，也就是把：

```text
status = 1
```

改成：

```text
status = 0
```

这叫软删除，也可以叫逻辑删除。

所以 `documentId = 1` 这条数据并没有从数据库消失，只是变成了归档状态。普通查询接口只查 `status = 1` 的文档，所以你看不到它。

你后来又创建了一篇新文档，数据库继续往后分配 id，所以新文档是：

```text
documentId = 2
```

## 02 为什么不能继续用 documentId = 1 测试

因为 `documentId = 1` 已经归档了。我们的业务代码查询文档时会加条件：

```text
status = 1
```

所以用 `documentId = 1` 去查详情、查 chunk，可能会返回：

```text
document not found
```

这不是数据库里没有这条记录，而是业务上认为它已经不可用了。

## 03 重启项目会不会让 id 变回 1

不会。

重启 Spring Boot 只会重启 Java 程序，不会清空 MySQL 数据库。

只要数据库里的表还在，自增 id 会继续往后走。比如现在已经有 `id = 1` 和 `id = 2`，下一次新建文档通常会是：

```text
documentId = 3
```

如果想让 id 重新从 1 开始，需要清空表并重置自增计数。但真实项目里一般不这么做，因为历史数据、日志、关联表都依赖这些 id。

## 04 为什么企业项目常用软删除

软删除的好处是：

- 可以保留历史记录，方便审计和排查问题。
- 用户误删后可以恢复。
- 关联数据不会突然断掉。
- 对知识库/RAG 项目来说，可以保留旧 chunk，用来分析文档更新前后的效果。

它的代价是：

- 查询时必须记得过滤 `status = 1`。
- 表里会保留更多历史数据。
- 后期可能需要归档清理策略。

DevMind 现在选择软删除，是因为它更接近真实业务系统，也更方便后面做 RAG 调试和评估。

## 05 当前已经跑通的链路

目前我们已经跑通：

```text
注册用户 -> 登录拿 token -> 创建知识库文档 -> 自动生成 chunk -> 查询文档和 chunk -> 归档文档
```

这里面涉及的核心概念：

- `user_account`：用户表。
- `knowledge_document`：原始文档表。
- `knowledge_document_chunk`：文档切片表。
- JWT：登录后用来证明身份的 token。
- chunk：RAG 检索时使用的小文本片段。
- 软删除：把数据状态改成归档，而不是从数据库直接删掉。

## 06 `.http` 里的 documentId 算不算硬编码

严格来说，之前 `.http` 文件里的：

```http
@documentId = 1
```

不算后端业务代码硬编码。它只是接口测试文件里的测试变量。

真正危险的硬编码一般是指在业务代码里写死某个值，比如：

```java
Long documentId = 1L;
```

这样会导致代码只能处理固定数据，换一个用户或换一篇文档就失效。

不过，测试文件里手动维护 `@documentId = 1` 也不够舒服。因为每次创建新文档后都要手动改 id，容易忘，也容易误操作旧数据。

所以我们把 `docs/api/devmind-api.http` 改成了自动保存创建结果：

```http
> {% client.global.set("documentId", response.body.data.id); %}
```

意思是：调用“创建文档”接口后，把返回结果里的 `data.id` 存成全局变量 `documentId`。

之后这些接口：

```http
GET /api/v1/documents/{{documentId}}
GET /api/v1/documents/{{documentId}}/chunks
PUT /api/v1/documents/{{documentId}}
DELETE /api/v1/documents/{{documentId}}
```

都会自动使用刚刚创建出来的文档 id。

## 07 归档后能不能回档

可以，但要分场景。

如果只是开发测试，最简单的回档方式是在数据库里把状态改回来：

```sql
UPDATE devmind.knowledge_document
SET status = 1
WHERE id = 1;
```

但文档的 chunk 也要恢复，否则后面检索不到内容。

如果这篇文档只有一版 chunk，可以这样恢复：

```sql
UPDATE devmind.knowledge_document_chunk
SET status = 1
WHERE document_id = 1;
```

如果文档经历过多次更新，chunk 表里可能保留了旧版本 chunk 和新版本 chunk。这时不能随便把所有旧 chunk 都恢复，否则检索时可能召回过期内容。

真实企业项目里更好的做法是：

- 提供一个“恢复归档文档”的后端接口。
- 或者给 chunk 增加版本号，只恢复最新版本。
- 或者重新根据文档正文生成一批新的 active chunk。

DevMind 当前阶段先不急着做恢复接口，因为我们还在学习主流程。测试时更推荐重新创建一篇新文档。

## 08 为什么下一步做 Search/Retrieval

RAG 的完整流程不是“用户问题直接丢给大模型”，而是：

```text
用户问题 -> 检索相关资料 -> 拼接上下文 -> 调用大模型生成回答
```

我们目前已经完成了前半段的数据准备：

```text
文档入库 -> 自动切片 chunk
```

所以自然的下一步是：

```text
用户输入关键词 -> 从 active chunk 里找相关片段
```

这就是 Search/Retrieval 模块。

## 09 为什么先做关键词检索，而不是一上来做向量检索

关键词检索不是最终形态，但它适合做 v0。

原因是：

- 它不依赖大模型 API。
- 不需要向量数据库。
- 方便验证权限、数据状态、chunk 表结构是否正确。
- 出问题时容易排查。

现在新增的接口是：

```text
GET /api/v1/search/chunks?keyword=Redis&limit=5
```

它只会检索当前登录用户自己的 active chunk，也就是：

```text
user_id = 当前用户
status = 1
content LIKE keyword
```

这体现了两个后端基本功：

- 数据隔离：用户只能查自己的数据。
- 状态过滤：归档数据不参与检索。

## 10 什么是 score

Search/Retrieval v0 返回结果里有一个 `score` 字段。

它不是 AI 算出来的分数，而是我们自己写的简单相关性分数：

- chunk 正文命中关键词，权重最高。
- 文档标题命中关键词，也加分。
- tags 命中关键词，也加分。
- sourceType 命中关键词，也加少量分。

这个 score 的意义不是追求完美，而是让结果有一个可解释排序。

以后升级方向是：

- 向量检索：根据语义相似度召回。
- 混合检索：关键词 + 向量一起用。
- rerank：对候选 chunk 再排序。
- 检索评估：记录问题、召回 chunk、答案质量，分析 bad case。

## 11 为什么先做 AI Ask Mock

现在新增的接口是：

```text
POST /api/v1/ai/ask
```

它代表 RAG 问答的第一版后端编排：

```text
用户问题 -> 提取检索关键词 -> 检索相关 chunk -> 组装上下文 -> 返回答案
```

这一版还没有调用真实大模型，所以返回里会有：

```json
{
  "modelProvider": "mock-local",
  "mock": true
}
```

这样做不是偷懒，而是工程上很常见的分阶段开发：

- 先把接口、数据结构、权限、检索链路跑通。
- 再接真实 LLM API。
- 如果后面模型调用失败，也能判断问题是在模型层，不是在业务链路。

## 12 `retrievalKeyword` 是什么

用户的问题可能是：

```text
Redis cache penetration 怎么解决？
```

如果我们直接拿整句话去数据库里做 `LIKE`，很可能查不到，因为 chunk 里不一定包含整句话。

所以 v0 做了一个很简单的关键词提取：

- 问题里包含 `Redis`，就用 `Redis` 检索。
- 包含 `MySQL`，就用 `MySQL` 检索。
- 包含 `JWT`，就用 `JWT` 检索。
- 否则尝试提取英文 token。

返回里的 `retrievalKeyword` 就是这一步提取出来的检索词。

这不是最终方案，但它能帮我们验证 RAG 主流程。

## 13 `retrievedChunks` 是什么

`retrievedChunks` 是系统根据问题找到的知识片段。

真实 RAG 里，大模型回答问题时不能只靠自己的参数记忆，而应该参考这些 chunk：

```text
retrievedChunks -> prompt context -> LLM answer
```

所以面试时可以这样讲：

```text
我把 RAG 流程拆成了检索和生成两层。先通过 retrieval 模块召回相关 chunk，再把 chunk 作为上下文交给回答模块。当前 v0 先用 mock answer 验证链路，后续可以替换成真实大模型调用。
```

## 14 下一步怎么升级

AI Ask v0 之后，可以继续升级：

1. 接入真实模型 API，比如 DeepSeek、通义千问或 OpenAI。
2. 保存问答日志，包括 question、retrievalKeyword、retrievedChunks、answer、耗时。
3. 做 bad case 分析，比如检索不到、召回不准、答案不忠实。
4. 引入向量检索和 rerank。

注意：真正的含金量不只是“能聊天”，而是能解释清楚：

- 问题如何进入系统。
- 如何检索资料。
- 为什么召回这些 chunk。
- 模型回答基于哪些上下文。
- 如何发现和改进坏结果。

## 15 为什么要按顺序点 `.http` 接口

在 IDEA 里点 `.http` 文件里的接口，本质上是在做接口联调，也可以理解成手工集成测试。

它不是随便点按钮，而是在验证一条真实业务链路：

```text
Login -> Create Knowledge Document -> List Document Chunks -> Search Chunks -> Ask AI Mock
```

每一步都有原因。

### Login

登录接口会返回 JWT token。

后面的文档、检索、AI Ask 接口都需要知道“当前用户是谁”，所以请求头里必须带：

```text
Authorization: Bearer <token>
```

如果不先登录，后面的接口会因为没有身份信息而失败。

### Create Knowledge Document

这一步是在创建测试数据。

如果数据库里没有 active 文档，后面的 chunk 检索和 AI Ask 就没有资料可查。

创建文档后，系统会自动做两件事：

```text
保存原文档 -> 生成 chunk
```

### List Document Chunks

这一步是在验证“自动切片”有没有成功。

如果创建了文档，但没有生成 chunk，后面的 RAG 检索就无法工作。

### Search Chunks

这一步是在验证 retrieval，也就是检索能力。

RAG 的核心不是直接问模型，而是先找相关资料：

```text
用户问题 -> 检索相关 chunk
```

### Ask AI Mock

这一步是在验证 RAG 问答编排：

```text
问题 -> 提取检索词 -> 检索 chunk -> 组装上下文 -> 返回答案
```

当前答案是 mock，不是真实大模型生成。但它能证明后端链路是通的。

### Archive Document

这个接口是归档文档，相当于软删除。

测试主流程时不要随便点它，因为点完后：

```text
document.status = 0
chunk.status = 0
```

后面的 Search 和 Ask 就查不到这篇文档了。

## 16 为什么 Codex 也可以帮你测接口

只要 DevMind 后端正在运行，我可以通过命令直接请求：

```text
http://localhost:8081
```

这样就不需要你在 IDEA 里一个个点。

但前提是：

- IDEA 里的 `DevMindApplication` 正在运行。
- 控制台里能看到 `Tomcat started on port 8081`。
- 本机浏览器能打开 `http://localhost:8081/swagger-ui.html`。

如果后端停了，我请求接口会失败，表现为：

```text
Unable to connect to the remote server
```

这不是代码错，只是服务没启动。

## 17 为什么要加 AI Ask 日志表

现在新增了 `ai_ask_log` 表，用来记录每次 AI Ask 请求。

它记录的信息包括：

- 用户 id：是谁问的。
- question：用户问了什么。
- retrievalKeyword：系统用什么关键词去检索。
- retrievedChunkCount：召回了多少 chunk。
- retrievedChunkIds：召回了哪些 chunk。
- answer：系统返回了什么答案。
- modelProvider：使用哪个模型或 mock provider。
- mock：是不是模拟答案。
- elapsedMs：这次问答耗时多久。

这张表的价值很大，因为真实 AI 项目不能只看“现在有没有返回答案”，还要能分析：

- 为什么这个问题没有召回资料。
- 为什么召回了错误资料。
- 哪些问题耗时很高。
- 哪些答案用户不满意。
- 后续模型升级后效果有没有变好。

这就是 bad case 分析和效果评估的基础。

## 18 为什么改了 schema.sql 还要单独执行 SQL

`schema.sql` 是项目里的数据库初始化脚本。

但是你的 MySQL 数据库 `devmind` 已经创建过了，Spring Boot 默认不会每次启动都重新执行这个脚本。

所以我们新增表以后，要手动执行一次迁移 SQL：

```text
docs/sql/20260623_create_ai_ask_log.sql
```

这类文件可以理解成“数据库版本变更脚本”。真实企业项目里通常会用 Flyway 或 Liquibase 管理这些迁移脚本。

当前阶段我们先手动执行，方便你理解数据库结构是怎么一步步演进的。

## 19 为什么日志查询也要做成接口

如果只把日志写进数据库，但没有查询接口，验证起来就很麻烦。

所以我们新增了：

```text
GET /api/v1/ai/ask-logs?pageNo=1&pageSize=10
```

这个接口只查询当前登录用户自己的日志，仍然保持用户数据隔离。

面试时可以这样讲：

```text
我为 AI Ask 增加了问答日志，用于记录问题、检索词、召回 chunk、答案、耗时和模型来源。这样后续可以做 bad case 分析、效果评估和成本监控，而不是只停留在能返回答案的 demo。
```

## 20 PromptBuilder 是什么

RAG 不是把用户问题直接交给大模型，而是要把问题和检索到的上下文组织成 prompt。

现在新增了 `PromptBuilderService`，它会把这些信息组合起来：

```text
系统角色说明
回答约束
用户问题
retrieved chunks
答案格式要求
```

核心约束是：

```text
只能基于提供的 context 回答。
如果 context 不足，要说明知识库信息不足。
引用使用到的 chunk ids。
```

这一步很重要，因为它是在减少大模型幻觉。模型不是随便发挥，而是被要求基于 retrieved context 回答。

## 21 promptPreview 为什么不是最终 prompt

接口里返回的是 `promptPreview`，不是完整正式 prompt。

原因是：

- 现在还没有接真实模型。
- prompt 可能很长，完整返回会让接口响应变大。
- preview 足够帮助开发调试：能看到问题、上下文、约束是否拼对。

后面接真实模型时，可以把完整 prompt 发给 LLM，同时日志里只保存 preview 或摘要，避免日志表过大。

## 22 citations 是什么

`citations` 表示答案引用了哪些 chunk。

例如：

```json
{
  "chunkId": 6,
  "documentId": 5,
  "documentTitle": "Redis cache penetration review",
  "score": 18
}
```

它的价值是可追踪：

- 用户知道答案依据来自哪里。
- 开发者能检查召回结果是否正确。
- 面试时能说明你考虑了 RAG 的可信度，而不是只做聊天接口。

真实 RAG 系统通常都需要引用来源，否则用户很难判断答案是不是胡说。

## 23 为什么这次还要执行 ALTER TABLE

我们给 `ai_ask_log` 增加了一个新字段：

```text
prompt_preview
```

项目里的 `schema.sql` 已经更新了，但你的数据库表已经存在了。MySQL 不会因为代码改了就自动给旧表加字段。

所以需要手动执行：

```text
docs/sql/20260623_add_prompt_preview_to_ai_ask_log.sql
```

内容是：

```sql
ALTER TABLE devmind.ai_ask_log
    ADD COLUMN prompt_preview MEDIUMTEXT DEFAULT NULL AFTER retrieval_keyword;
```

真实企业里这类操作一般会用 Flyway/Liquibase 管理，避免手动漏执行。

## 24 为什么要抽象 LlmClient

现在 AI Ask 里新增了 LLM 调用抽象：

```text
LlmClient
MockLlmClient
LlmClientRouter
AiProperties
```

这样做的目的是解耦。

原来的流程是：

```text
AiAskService 直接生成 mock answer
```

这样写短期能跑，但后面接 DeepSeek、通义千问或 OpenAI 时，`AiAskService` 会越来越乱。

现在改成：

```text
AiAskService -> LlmClientRouter -> LlmClient 实现类
```

`AiAskService` 只关心 RAG 编排：

```text
提问 -> 检索 -> 构造 prompt -> 调用 LLM -> 写日志
```

具体用哪个模型，由 `LlmClient` 实现类负责。

## 25 MockLlmClient 有什么意义

`MockLlmClient` 是一个本地假模型实现。

它不调用外部 API，不需要 API Key，也不会产生费用。

它的价值是：

- 先把业务链路跑通。
- 让接口返回结构稳定。
- 让日志、citations、promptPreview 都能测试。
- 后面真实模型接入失败时，可以切回 mock 排查问题。

当前配置是：

```yaml
devmind:
  ai:
    provider: mock
```

也可以通过环境变量设置：

```text
DEVMIND_AI_PROVIDER=mock
```

## 26 后续怎么接真实模型

以后接 DeepSeek 或通义千问时，不需要推翻现在的代码。

只需要新增一个实现类，例如：

```text
DeepSeekLlmClient implements LlmClient
```

然后让它支持：

```java
supports("deepseek")
```

配置改成：

```text
DEVMIND_AI_PROVIDER=deepseek
```

这样 `AiAskService` 不需要知道底层到底是 mock、DeepSeek 还是通义千问。

面试时可以这样讲：

```text
我没有把模型调用硬编码在业务 Service 里，而是定义了 LlmClient 接口和路由层。AI Ask 只负责 RAG 编排，模型调用由具体 provider 实现。当前使用 MockLlmClient 跑通链路，后续可以无侵入接入 DeepSeek 或通义千问。
```

## 27 DeepSeekLlmClient 做了什么

现在新增了：

```text
DeepSeekLlmClient
```

它实现了：

```java
LlmClient
```

当配置为：

```text
DEVMIND_AI_PROVIDER=deepseek
```

时，`LlmClientRouter` 会把请求交给 `DeepSeekLlmClient`。

DeepSeek 官方 API 兼容 OpenAI 风格的 chat completions，所以请求结构大致是：

```text
POST https://api.deepseek.com/chat/completions
Authorization: Bearer <API_KEY>
```

请求体包含：

```json
{
  "model": "deepseek-v4-flash",
  "messages": [
    {"role": "system", "content": "..."},
    {"role": "user", "content": "..."}
  ],
  "temperature": 0.2,
  "stream": false
}
```

## 28 为什么 API Key 不能写进代码

API Key 相当于你的付费账号密码。

不能写在：

- Java 代码里。
- `application.yml` 的固定值里。
- README 示例的真实值里。
- GitHub 仓库里。

正确做法是使用环境变量：

```text
DEVMIND_DEEPSEEK_API_KEY=你的真实 key
```

代码里只读取环境变量：

```yaml
deepseek-api-key: ${DEVMIND_DEEPSEEK_API_KEY:}
```

这样即使代码上传 GitHub，也不会泄露 key。

## 29 为什么默认还是 mock

虽然已经写好了 `DeepSeekLlmClient`，但默认配置仍然是：

```text
DEVMIND_AI_PROVIDER=mock
```

原因是：

- 没有 API Key 时项目也能启动。
- 本地开发不一定每次都想花钱调用模型。
- 联调 RAG 主流程时，mock 更稳定。
- 出问题时可以区分是业务链路问题，还是模型 API 问题。

当你真的要测试 DeepSeek，只需要在 IDEA 运行配置里加：

```text
DEVMIND_AI_PROVIDER=deepseek
DEVMIND_DEEPSEEK_API_KEY=你的真实 key
DEVMIND_DEEPSEEK_MODEL=deepseek-v4-flash
```

然后重启后端即可。
## 30 真实 DeepSeek 调用验证

这一步的目标是确认 DevMind 已经从本地 mock 模型切换到了真实 DeepSeek API。

我们在 IDEA 的 `DevMindApplication` 运行配置里加入了：

```text
DEVMIND_AI_PROVIDER=deepseek
DEVMIND_DEEPSEEK_API_KEY=你的真实 key
DEVMIND_DEEPSEEK_MODEL=deepseek-v4-flash
```

然后重启后端。重启是必须的，因为 Spring Boot 只会在应用启动时读取环境变量。

测试链路是：

```text
注册临时用户 -> 登录获取 JWT -> 创建知识文档 -> 提问 -> 检索 chunk -> 构造 prompt -> 调用 DeepSeek -> 写入 ai_ask_log
```

本次验证结果：

```text
modelProvider = deepseek:deepseek-v4-flash
mock = false
promptPreviewPresent = true
citationCount = 1
```

其中最关键的是：

```text
mock = false
```

这说明返回结果不是 `MockLlmClient` 生成的，而是真实调用了 DeepSeek。

面试时可以这样讲：

```text
项目默认使用 MockLlmClient 保证本地开发稳定；当配置 DEVMIND_AI_PROVIDER=deepseek 并注入 API Key 后，LlmClientRouter 会把请求路由到 DeepSeekLlmClient。AI Ask 接口会先检索相关知识片段，再构建带上下文和引用约束的 Prompt，最后调用真实模型并把问题、Prompt、召回 chunk、模型 provider、耗时等信息写入日志表，方便后续做可观测和 RAG 效果分析。
```
## 31 为什么要记录 token usage

真实模型不是免费的，每次调用都会按照输入和输出 token 计费。

所以 `ai_ask_log` 现在新增了三个字段：

```text
prompt_tokens
completion_tokens
total_tokens
```

它们分别表示：

- `prompt_tokens`：发送给模型的输入 token 数，包含系统提示词、用户问题、召回的知识片段。
- `completion_tokens`：模型生成答案消耗的输出 token 数。
- `total_tokens`：本次调用总 token 数。

这一步的价值不是“多存几个字段”，而是让 AI 调用具备可观测性：

```text
一次问答是否太贵？
prompt 是否塞了太多无关 chunk？
用户问题是否导致输出过长？
后续做 RAG 评估时，效果和成本能不能一起比较？
```

面试时可以这样讲：

```text
我没有只停留在把大模型 API 调通，而是把每次 AI 调用的 provider、耗时、召回 chunk、prompt preview 和 token usage 都记录到日志表里。这样后续可以分析 bad case，也可以根据 prompt_tokens 和 completion_tokens 做成本统计，判断 RAG 检索是否召回了过多无关上下文。
```

本机已有数据库需要执行迁移：

```sql
USE devmind;

ALTER TABLE ai_ask_log
    ADD COLUMN prompt_tokens INT DEFAULT NULL AFTER mock,
    ADD COLUMN completion_tokens INT DEFAULT NULL AFTER prompt_tokens,
    ADD COLUMN total_tokens INT DEFAULT NULL AFTER completion_tokens;
```
## 32 Bad Case 反馈为什么有价值

RAG 系统不能只看“有没有回答”，还要看“回答好不好”。

所以现在新增了一张表：

```text
ai_ask_feedback
```

它记录用户对某次 AI 问答的反馈：

```text
ask_log_id
helpful
reason
expected_answer
created_at
```

它和 `ai_ask_log` 的关系是：

```text
一次 AI 问答日志 -> 可以有多条反馈记录
```

第一版接口：

```text
POST /api/v1/ai/ask-logs/{logId}/feedback
GET  /api/v1/ai/ask-feedback?helpful=false&pageNo=1&pageSize=10
```

为什么要做这个？

因为真实 AI 项目一定会遇到回答不准、召回不准、引用不够、答案太泛的问题。

如果没有 feedback 表，这些问题只能靠记忆，后续无法系统分析。

有了 bad case 记录后，后面可以做：

```text
统计哪些问题经常回答不好
分析是检索没召回，还是 prompt 没约束好
把 expected_answer 当作人工标注答案
后续做 RAG 评估集
比较改 prompt、改检索策略前后的效果
```

面试时可以这样讲：

```text
我在 AI Ask 日志之外又设计了 feedback 表，用户可以对某次回答标记 helpful 或 bad case，并记录原因和期望答案。这样项目不是一次性 demo，而是具备质量反馈闭环。后续可以基于这些 bad case 做 prompt 迭代、检索策略优化和离线评估。
```

本机已有数据库需要执行迁移：

```sql
USE devmind;

CREATE TABLE IF NOT EXISTS ai_ask_feedback (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    ask_log_id BIGINT NOT NULL,
    helpful TINYINT NOT NULL COMMENT '1 helpful, 0 bad case',
    reason VARCHAR(500) DEFAULT NULL,
    expected_answer MEDIUMTEXT DEFAULT NULL,
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1 active, 0 deleted',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_created (user_id, created_at),
    INDEX idx_user_helpful (user_id, helpful, created_at),
    INDEX idx_ask_log (ask_log_id),
    CONSTRAINT fk_ai_ask_feedback_user FOREIGN KEY (user_id) REFERENCES user_account(id),
    CONSTRAINT fk_ai_ask_feedback_log FOREIGN KEY (ask_log_id) REFERENCES ai_ask_log(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```
## 33 RAG 评估统计接口

只有 `ai_ask_feedback` 表还不够，因为表只是原始数据。

为了让 bad case 真正能被分析，现在新增了统计接口：

```text
GET /api/v1/ai/evaluation/summary?recentLimit=5
```

它返回：

```text
totalFeedbackCount
helpfulCount
badCaseCount
badCaseRate
recentBadCases
```

其中：

- `totalFeedbackCount`：当前用户一共提交了多少条反馈。
- `helpfulCount`：多少条认为回答有用。
- `badCaseCount`：多少条认为回答不好。
- `badCaseRate`：bad case 占比，0.25 表示 25%。
- `recentBadCases`：最近的 bad case 列表，包含问题、原因和期望答案。

为什么要做这个接口？

因为真实项目里，光把反馈存起来还不够，还要能回答：

```text
最近 AI 回答质量怎么样？
bad case 比例高不高？
哪些问题经常答不好？
下一步应该优化 prompt，还是优化检索？
```

面试时可以这样讲：

```text
我基于 feedback 表做了一个 RAG 评估统计接口，聚合总反馈数、bad case 数、bad case rate 和最近 bad case。它相当于一个轻量质量看板，可以帮助后续判断 RAG 链路是否需要调整 prompt、检索策略或知识库内容。
```
## 34 为什么引入 Flyway

一开始我们通过 DBeaver 手动执行 SQL：

```text
schema.sql
docs/sql/*.sql
```

这种方式适合学习和快速验证，但不够企业化。

问题是：

```text
新电脑怎么知道要执行哪些 SQL？
执行顺序怎么保证？
某个字段是不是已经加过？
团队里不同人的数据库结构会不会不一致？
上线时怎么追踪数据库版本？
```

所以现在引入 Flyway。

Flyway 的规则是把数据库变更写成版本化脚本：

```text
src/main/resources/db/migration/V1__init_schema.sql
```

命名规则：

```text
V版本号__说明.sql
```

例如：

```text
V1__init_schema.sql
V2__add_vector_columns.sql
V3__add_rerank_score.sql
```

项目启动时，Flyway 会自动检查哪些迁移已经执行，哪些还没执行，然后只执行新的迁移。

因为我们本地数据库之前已经手动建过表，所以配置了：

```yaml
spring:
  flyway:
    baseline-on-migrate: true
    baseline-version: 1
```

含义是：如果数据库已经不是空库，并且没有 Flyway 历史表，就把当前数据库结构当作 V1 基线接管，避免重复执行建表脚本。

面试时可以这样讲：

```text
项目最开始为了快速验证使用手动 SQL，后面我引入 Flyway 做数据库版本管理。新环境只需要创建数据库，应用启动时会自动执行迁移；已有数据库则通过 baseline-on-migrate 接管，后续表结构变更都可以通过 V2、V3 这类脚本追踪。
```

## 35 为什么要给核心逻辑补单元测试

这次新增的测试不是为了“显得项目很复杂”，而是为了保护核心业务规则。

当前 DevMind 里最容易被后续修改影响的地方有：

- Prompt 构建：问题、上下文、引用要求必须被正确拼进 prompt。
- Mock 模型：没有真实 API Key 时，本地链路仍然要稳定可测。
- LLM 路由：`DEVMIND_AI_PROVIDER` 改成不同 provider 时，要能路由到正确实现。
- JWT：登录后生成的 token 必须能正确解析出用户身份。

所以新增了这些测试：

```text
PromptBuilderServiceTest
MockLlmClientTest
LlmClientRouterTest
JwtTokenProviderTest
```

它们都是不依赖 MySQL、Redis、DeepSeek API 的单元测试。这样做的好处是：

- 跑得快。
- 不受本机数据库状态影响。
- 不消耗 API 余额。
- 能快速发现核心逻辑被改坏。

这次测试命令是：

```powershell
mvn test
```

最终结果：

```text
Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

面试时可以这样讲：

```text
我为 RAG 问答链路中的 Prompt 构建、模型路由、Mock 模型回答和 JWT 鉴权补充了单元测试。这些测试不依赖外部数据库和真实大模型 API，能够在本地快速验证核心业务规则，避免后续迭代 Prompt、Provider 或鉴权逻辑时引入回归问题。
```

## 36 为什么要加 GitHub Actions CI

本地执行 `mvn test` 只能证明“在我这台电脑上测试通过”。

GitHub Actions CI 的作用是：每次代码 push 到 GitHub，或者以后创建 Pull Request 时，GitHub 自动在云端拉取代码、安装 Java、执行测试。

这次新增的文件是：

```text
.github/workflows/ci.yml
```

它做了几件事：

```text
checkout 代码
安装 Java 17
缓存 Maven 依赖
执行 mvn test
```

为什么用 Java 17？

因为 `pom.xml` 里项目声明的是：

```xml
<java.version>17</java.version>
```

虽然本机 IDEA 当前使用 Java 19 也能跑，但 CI 使用项目声明的 Java 17 更规范，也更接近企业项目对版本一致性的要求。

面试时可以这样讲：

```text
我在项目中加入了 GitHub Actions CI，每次 push 或 PR 都会自动使用 Java 17 执行 Maven 测试。这样可以保证核心单元测试在独立环境中通过，避免只在本机可用，也体现了基本的工程化质量保障流程。
```

## 37 为什么要优化为中英文多关键词检索

最初的 AI Ask 检索策略比较简单：

```text
用户问题 -> 取一个关键词 -> 用这个关键词检索 chunk
```

例如问题里包含 `Redis`，就只用 `Redis` 去检索。

这个方式能跑通 v0 链路，但有明显短板：

- 中文问题不一定包含英文关键词。
- 一个问题里可能有多个关键概念。
- 只检索一个词，容易漏掉更相关的 chunk。

所以现在新增了：

```text
RetrievalKeywordService
```

它负责从用户问题中提取多个检索词。例如：

```text
问题：Redis 缓存穿透和缓存雪崩有什么区别？
检索词：Redis, 缓存穿透, 缓存雪崩
```

再例如：

```text
问题：如何解释 JWT 鉴权和登录流程？
检索词：JWT, 鉴权, 登录
```

`ChunkSearchService` 也从单关键词升级成了多关键词检索：

```text
content LIKE Redis OR content LIKE 缓存穿透 OR content LIKE 缓存雪崩
```

打分时也会把多个关键词的命中情况加总：

```text
chunk 正文命中：权重最高
文档标题命中：加分
tags 命中：加分
sourceType 命中：少量加分
```

这不是向量检索，但它比单关键词 v0 更适合中文技术笔记场景，也更容易调试。

这次还补了单元测试：

```text
RetrievalKeywordServiceTest
```

验证了：

- 能提取 `Redis`、`缓存穿透`、`缓存雪崩`。
- 能提取 `JWT`、`鉴权`、`登录`。
- 能过滤常见英文疑问词，比如 `how`、`should`、`interview`。

面试时可以这样讲：

```text
项目早期的 RAG 检索只使用单个英文关键词，中文问题和多概念问题的召回效果较弱。后面我抽象了 RetrievalKeywordService，把检索词提取从单关键词升级成中英文多关键词，并让 ChunkSearchService 支持 OR 条件召回和多关键词加权打分。这样在不引入向量库的前提下，提高了中文技术笔记场景下的上下文命中率，同时保持了检索结果可解释、易调试。
```

## 38 为什么要给 JWT logout 接 Redis 黑名单

JWT 的特点是无状态：服务端签发 token 后，后续请求只要 token 没过期、签名正确，就可以通过认证。

这带来一个问题：

```text
用户点击 logout 后，服务端默认并不知道要让哪个 token 立刻失效。
```

所以这次把 Redis 真正用到了认证链路里：

```text
用户登录 -> 得到 JWT
用户 logout -> 把当前 JWT 的 SHA-256 哈希写入 Redis
Redis key 设置 TTL = token 剩余有效期
后续请求 -> JwtAuthenticationFilter 先查 Redis 黑名单
命中黑名单 -> 不建立登录态
```

这里有两个设计点：

- 不把原始 token 明文存进 Redis，而是存 SHA-256 哈希，避免 Redis 数据暴露时 token 直接可用。
- TTL 使用 token 剩余有效期，过期后 Redis 自动删除，不需要额外清理任务。

面试时可以这样讲：

```text
JWT 本身是无状态的，服务端默认无法主动吊销 token。为了解决 logout 后 token 仍可用的问题，我用 Redis 做 token blacklist。登出时把 token 的 SHA-256 哈希写入 Redis，并设置与 token 剩余有效期一致的 TTL；认证过滤器解析 token 前先检查黑名单，命中则拒绝建立认证上下文。这样既保留了 JWT 的无状态优势，又支持了服务端主动登出失效。
```

## 39 为什么 AI 调用失败也要落库

AI 项目不能只记录成功路径。真实接入 DeepSeek 这类模型时，可能出现：

- API key 没配置。
- provider 名称配置错。
- 网络超时。
- 模型服务返回异常。

如果失败时只给前端返回错误，不写日志，后面就很难排查：

```text
到底是检索没召回？
还是 prompt 有问题？
还是 provider 调用失败？
```

所以现在失败时也会写入 `ai_ask_log`：

```text
status = 0
question
retrieval_keyword
prompt_preview
model_provider
retrieved_chunk_ids
elapsed_ms
answer = LLM request failed: 具体失败原因
```

面试时可以这样讲：

```text
我没有只处理 AI 调用成功的 happy path。模型 provider 调用失败时，我仍然会记录问题、检索词、prompt preview、召回 chunk、provider、耗时和失败原因，并把 status 标记为 0。这样后续可以区分是 RAG 召回问题、Prompt 问题，还是模型服务/配置问题，属于 AI 应用的可观测性设计。
```

## 40 为什么要做无召回兜底

RAG 的核心原则是：回答应该基于检索到的知识库上下文。

如果检索结果为空，还继续把问题交给大模型，就会有两个风险：

- 模型可能凭自己的通用知识回答，造成“看起来合理但没有知识库依据”的幻觉。
- 白白消耗 token 和 API 成本。

所以现在 `AiAskService` 在检索后做了一层判断：

```text
如果 retrieved chunks 为空：
    不调用 LLM provider
    直接返回固定兜底回答
    写入 ask log
如果 retrieved chunks 不为空：
    构造 prompt
    调用 Mock/DeepSeek
    写入成功或失败日志
```

兜底回答的含义是：

```text
知识库目前没有足够信息回答这个问题，请补充相关笔记或换一种问法。
```

这一步的价值不是让项目显得复杂，而是让 AI 行为更可控。

面试时可以这样讲：

```text
为了减少幻觉和无效 token 消耗，我在 RAG 编排层加了无召回兜底。如果关键词检索没有召回任何 chunk，系统不会继续调用大模型，而是直接返回“知识库暂无足够信息”的确定性回答，并记录日志。这样可以保证回答边界清晰，也方便后续通过 bad case 或补充文档来优化知识库覆盖率。
```
## 45 为什么这一轮先做 HybridRetrievalStrategy

这一轮不是直接上外部向量数据库，而是先把检索层从业务流程里抽出来，做成可以替换的 `RetrievalStrategy`。

原因是：

- AI Ask 和 RAG Evaluation 不应该直接依赖某一种检索实现。
- 如果后面接入真实 embedding、向量库或 rerank，不应该大改 `AiAskService`。
- 先有 gold label 的 Hit@3/MRR，后续换检索策略时才能比较效果。

当前主策略是 `HybridRetrievalStrategy`：

```text
用户问题
-> 提取多个检索词
-> KeywordRetrievalStrategy 做 MySQL FULLTEXT / 关键词 / 元数据召回
-> LocalEmbeddingClient 把问题和 chunk 转成稀疏向量
-> 用 cosine similarity 得到本地稀疏向量排序
-> 用 RRF 融合关键词 / FULLTEXT 排名和本地稀疏向量排名
-> 返回 TopK chunk
```

这里的 `LocalEmbeddingClient` 不是生产级 embedding 模型，也不是向量数据库。它只是一个本地、确定性、可测试的相似度实现：

- 英文用 token。
- 中文用相邻汉字 bigram。
- 向量归一化后用余弦相似度算分。

面试时要诚实讲：

```text
当前版本已经有 hybrid retrieval 的工程结构、本地稀疏向量排序和 RRF 融合排序，但还没有接外部 embedding API 和向量数据库。这里的本地稀疏向量不是神经网络 embedding，它只是词袋 + 中文 bigram 的确定性向量空间模型。这样做的目的是先把策略抽象、评估基线和问答链路稳定下来，下一步可以把本地实现替换成真实 embedding provider，并用同一套 Hit@3/MRR 评估集对比提升。
```

这一步的价值不是“我已经做了生产级向量检索”，而是：

- 检索策略解耦了。
- 评估集可以复用。
- 后续接真实向量库时风险更小。
- 简历上可以讲清楚从关键词检索到混合检索的演进过程。

## 46 为什么还要抽象 EmbeddingClient

上一轮 `HybridRetrievalStrategy` 已经能把关键词召回和本地相似度重排合在一起，但如果业务代码直接依赖 `LocalEmbeddingClient`，后面接真实 embedding 模型时就需要改检索策略本身。

所以这一轮把 embedding 能力抽成 `EmbeddingClient` 接口：

```text
HybridRetrievalStrategy
-> EmbeddingClient 接口
-> LocalEmbeddingClient 本地实现
-> 未来可以新增 DeepSeekEmbeddingClient / QwenEmbeddingClient / VectorStoreEmbeddingClient
```

这样做的价值是：

- 检索编排层只关心“把文本转成向量、计算相似度”，不关心具体 provider。
- 本地开发可以继续使用确定性的 local sparse vector，测试稳定、成本为 0。
- 后续接真实 embedding API 或向量数据库时，可以新增实现类，而不是重写 RAG 主流程。

面试时可以这样讲：

```text
我没有把 embedding 实现硬编码进检索策略，而是抽象了 EmbeddingClient。当前 LocalEmbeddingClient 用本地稀疏向量保证可测试和低成本，后续可以替换为真实 embedding provider 或向量库实现。这样 AI Ask、评估集和检索策略不用大改，符合面向接口编程和可演进设计。
```

后面需要让 Claude Code 做一次阶段性审查，重点看：

- `HybridRetrievalStrategy` 是否真的走进 AI Ask 和 RAG Evaluation 主链路。
- `EmbeddingClient` 抽象是否合理，是否有过度设计。
- README / resume / interview guide 有没有把本地稀疏向量过度包装成生产级向量库。
# 2026-06-30

## 统一 embedding 输入文本

这一步不是为了立刻“堆新技术”，而是为了让后续真正接 embedding API、向量表或向量数据库时有稳定边界。

项目新增 `EmbeddingTextBuilder`，统一规定 chunk 做相似度计算时使用这些字段：

- 文档标题
- 来源类型
- 标签
- chunk 内容

这样做的原因：

1. 只用 chunk 内容可能漏掉标题和标签里的重要技术词。
2. 如果索引时和查询重排时拼接字段不一致，评估结果会不稳定。
3. 后续把本地 sparse vector 换成 DeepSeek / 通义 embedding 或向量库时，只需要复用同一个文本构造逻辑。

面试可以这样讲：

```text
我把 embedding 输入文本单独抽成 EmbeddingTextBuilder，而不是散落在检索代码里手拼字符串。这样后续无论是本地向量、外部 embedding API 还是向量库持久化，都能保证索引和检索用的是同一套字段。
```

## 47 为什么要持久化 chunk 稀疏向量

之前的混合检索已经能做本地稀疏向量余弦重排，但有一个工程问题：每次提问时都会临时取一批 chunk，然后现场给每个 chunk 计算向量。

对现在的 `LocalEmbeddingClient` 来说，这个成本很低，因为它只是词袋 + 中文 bigram 的本地计算。但如果以后换成真实 embedding API，这种写法就会变成：

```text
一次提问
-> 取 120 个候选 chunk
-> 给 120 个 chunk 逐个调用 embedding API
-> 再给 query 调一次 embedding API
```

这会很慢、很贵，也不符合真实 RAG 系统的做法。真实系统通常是在文档入库或 chunk 重建时就把 chunk 向量算好并存起来，提问时只计算 query 向量。

现在 DevMind 做了这一步：

```text
导入或更新文档
-> DocumentChunkService 重建 chunks
-> EmbeddingTextBuilder 统一拼接标题、类型、标签、chunk 内容
-> EmbeddingClient 生成本地稀疏向量
-> 写入 knowledge_document_chunk_vector

用户提问
-> 只计算 query 向量
-> 读取已持久化的 active chunk vectors
-> 计算 cosine similarity
-> 用 RRF 和关键词 / FULLTEXT 排名融合
```

这一步的含金量不在于“已经有生产级向量数据库”，而在于把 RAG 的索引阶段和查询阶段分开了：

- 索引阶段：文档变化时生成 chunk 向量。
- 查询阶段：问题来时只算 query 向量。
- 数据层：有独立的 `knowledge_document_chunk_vector` 表保存向量和 provider。
- 扩展点：以后可以把本地稀疏向量替换成真实 embedding provider 或向量库。

面试时可以这样讲：

```text
我没有在每次提问时重新计算所有 chunk 向量，而是在 chunk 重建时同步生成并持久化本地稀疏向量。这样 query path 只需要计算问题向量，再和已存储的 chunk 向量做相似度比较。当前实现仍然是本地稀疏向量，不是外部 embedding 或向量数据库，但它已经把索引和检索的工程边界拆开了，后续接真实 embedding provider 时不会重写主链路。
```

这批改动之后，需要让 Claude Code 再审一次，因为它动到了数据库迁移、chunk 重建事务、向量归档和混合检索主链路。

## 48 为什么把分数相加改成 RRF

之前的混合检索已经能把关键词召回和本地稀疏向量相似度结合起来，但如果只是把两个分数直接相加，会有一个隐藏问题：两路分数不是同一种东西。

- 关键词 / FULLTEXT 分数来自内容、标题、标签、类型等字段的命中权重。
- 本地稀疏向量分数来自 cosine similarity。
- 如果直接相加，某一路分数范围更大，就可能长期主导最终排序。

RRF（Reciprocal Rank Fusion）的思路是不直接相信原始分数，而是使用“排名”来融合：

```text
finalScore += 1 / (k + rank)
```

这样做的好处是：

- 不需要强行把关键词分数和向量相似度调到同一个量纲。
- 两路召回都排得靠前的 chunk 会被自然提升。
- 后续接外部 embedding、向量库或 rerank 时，可以继续用同一套融合思路做对比。

这一轮还把评估接口升级成 keyword baseline 与当前 hybrid/RRF 策略对比：

```text
keyword baseline Hit@3 / MRR
hybrid RRF Hit@3 / MRR
delta
```

面试时可以这样讲：

```text
我没有直接把关键词分数和向量相似度相加，因为它们不是同一类分数。当前版本用 RRF 按排名融合关键词 / FULLTEXT 召回和本地稀疏向量排序，并用同一套 gold-label evaluation dataset 输出 keyword baseline 与 hybrid/RRF 的 Hit@3、MRR 和 delta。这样后续接真实 embedding 或向量库时，可以用同一把尺子比较是否真的提升。
```

诚实边界：

- 现在仍然是本地稀疏向量，不是外部 embedding 模型。
- 现在仍然不是生产级向量数据库。
- RRF 是排序融合 baseline，后续还可以继续接专业 rerank。

这批改动之后，需要让 Claude Code 专门审一次“RRF 融合排序 + keyword baseline 对比 + Hit@3/MRR 指标”，因为它会影响简历里最核心的检索质量说法。

## 49 为什么要加入词法失配评估 case

如果评估集里的问题和文档标题、标签、内容关键词完全一样，关键词 baseline 很容易拿满分。这样即使我们做了本地稀疏向量、RRF 或后续真实 embedding，也很难看出新策略到底有没有帮助。

所以这一步新增了两类轻度改写问题：

- 不直接问“缓存穿透”，而是问“不存在的 key 一直打到 MySQL 怎么办”。
- 不直接问“JWT 黑名单”，而是问“用户退出后旧 token 还没过期，后端怎么拦截”。

这些问题的 gold label 仍然是人工标注的相关文档标题，例如 `Redis 缓存穿透复盘`、`JWT 退出登录与 Redis 黑名单`。评估时系统不会用“是否包含某个关键词”来判定相关，而是看 TopK 召回里是否命中这些 gold 文档。

这样做的意义：

1. 给关键词 baseline 制造一点真实难度，避免评估长期满分。
2. 检查 hybrid/RRF 在轻度改写问题下是否能守住相关文档。
3. 为后续接真实 embedding 做同一套评估集对比。

需要诚实说明的是：当前本地稀疏向量仍然是词袋 + 中文 bigram 的余弦相似度，不是真正的语义 embedding。它能帮助处理部分词面相近的改写，但如果问题和文档表达完全不同，仍然需要外部 embedding 模型或向量数据库来解决。

面试时可以这样讲：

```text
我没有只用关键词命中来证明检索效果，因为那样容易自己评自己。项目里加了 gold-label evaluation dataset，并且故意加入一些轻度改写问题，例如不直接问“缓存穿透”，而是问“不存在 key 一直打到 MySQL 怎么办”。这样可以用同一套 Hit@3/MRR 指标比较 keyword baseline、当前 hybrid/RRF，以及后续真实 embedding 的效果差异。
```

## 54 为什么评估要同时输出 keyword / sparse-hybrid / dense-hybrid

- 为什么：同一套 gold-label case 同时跑 keyword baseline、显式 `local-sparse-vector` hybrid、显式 `remote-dense` hybrid，才能把 Stage B 的提升讲成可复现的 Hit@3/MRR 对比，而不是只看单次问答体感。面试可能追问：三条链路是否共用同一批 case、同一个 TopK、同一种 gold-label 判定。
- 为什么：dense-hybrid 在 remote provider 未配置或调用失败时标记为 `unavailable`，不影响 keyword 和 sparse 评估返回，避免默认无 key 环境 500 或产生费用。面试可能追问：如何证明默认无 key 不会联网，以及失败是否会污染其它指标。
- 为什么：回填入口只接 `POST /api/v1/ai/embedding/backfill?provider=...` 并使用当前认证用户的 `userId`，具体幂等与 provider 守卫仍放在 `ChunkVectorService`。面试可能追问：为什么 controller 不直接操作 mapper，以及如何保证不同用户的数据 scope。
- 为什么：旧的评估字段继续表示 sparse-hybrid 相对 keyword baseline 的结果，同时新增 `strategyResults` 承载三方指标，降低既有调用方迁移成本。面试可能追问：API 演进时如何做到向后兼容。
