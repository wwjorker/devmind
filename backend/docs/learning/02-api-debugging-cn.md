# 02 接口联调学习笔记

这一阶段的目标不是接大模型，而是先把后端业务链路跑通：

1. 注册用户。
2. 登录获取 JWT。
3. 带着 JWT 调用需要登录的接口。
4. 创建知识库文档。
5. 查看系统自动生成的 chunk。

## 为什么要先做联调

只看代码很容易产生“我好像懂了”的错觉。真实后端开发一定要跑接口，因为接口联调会暴露这些问题：

- 数据库表是否真的存在。
- JSON 字段名是否和 DTO 对得上。
- JWT 是否正确放在 `Authorization` 请求头里。
- 当前用户的数据是否被 `userId` 隔离。
- 创建文档后是否真的生成了 chunk。

## JWT 怎么理解

登录成功后，后端返回一个 token。之后前端每次访问需要登录的接口，都要带上：

```text
Authorization: Bearer <token>
```

后端的 `JwtAuthenticationFilter` 会解析这个 token，并把当前用户信息放进 Spring Security 上下文。Controller 里通过：

```java
@AuthenticationPrincipal AuthenticatedUser user
```

拿到当前登录用户。

## chunk 怎么理解

RAG 不适合直接把整篇长文档丢给大模型。更常见的做法是：

1. 保存原文档。
2. 把文档拆成多个小段，也就是 chunk。
3. 后续对 chunk 做 embedding。
4. 用户提问时，先召回最相关的 chunk，再交给大模型回答。

现在 DevMind 已经完成了第 1 步和第 2 步。

## 你需要重点看哪些代码

- `KnowledgeDocumentController`：HTTP 接口入口。
- `KnowledgeDocumentService`：文档业务流程。
- `DocumentChunkService`：文档切片逻辑。
- `JwtAuthenticationFilter`：JWT 鉴权入口。
- `schema.sql`：数据库表结构。

## 面试可以怎么讲

可以这样说：

```text
我在知识库模块里没有直接把长文档交给大模型，而是先做文档切片。
创建文档时系统会自动生成 chunk，更新文档时会归档旧 chunk 并重建新 chunk。
这样后续做 embedding 和向量检索时，召回的是更细粒度的文本片段，可以减少 token 浪费，也更容易做召回质量评估。
```
