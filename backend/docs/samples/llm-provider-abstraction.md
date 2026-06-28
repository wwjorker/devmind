# LLM Provider 抽象设计

## 问题

如果在 `AiAskService` 里直接写 DeepSeek HTTP 调用，业务流程会和某一个模型供应商强绑定。后续想切换模型、做本地测试或增加兜底 Provider，都需要改核心业务代码。

## 设计

DevMind 抽象了 `LlmClient` 接口，并通过 `LlmClientRouter` 选择具体 Provider：

```text
AiAskService
-> LlmClientRouter
-> LlmClient
-> MockLlmClient / DeepSeekLlmClient
```

`MockLlmClient` 用于本地开发和测试，不需要真实 API Key，也不会产生模型费用。

`DeepSeekLlmClient` 用于真实模型调用，通过环境变量配置模型名、API Key 和调用参数。

## 价值

- 业务服务不依赖某个厂商 API。
- 本地开发默认使用 Mock，稳定且低成本。
- 演示或真实问答时切换到 DeepSeek。
- 后续可以扩展其他 Provider 或 provider fallback。

## 面试表达

我没有把 DeepSeek 调用直接写死在业务 Service 里，而是通过 `LlmClient` 接口隔离模型供应商。这样可以支持 Mock/DeepSeek 切换，方便本地测试、控制成本，也避免 RAG 主链路和单一模型厂商强耦合。
