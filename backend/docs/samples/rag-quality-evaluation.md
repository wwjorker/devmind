# RAG 回答质量评估

## 问题

AI 问答系统不能只看“有没有回答”，还要判断回答是否基于正确上下文、是否召回了相关 chunk、是否覆盖了期望知识点。

如果没有评估机制，项目很容易停留在一次性 demo：能回答，但不知道回答质量是否稳定。

## 设计

DevMind 设计了三层质量反馈：

1. `ai_ask_log` 记录每次问答的检索词、召回 chunk、Prompt Preview、token 用量、耗时和模型 Provider。
2. `ai_ask_feedback` 记录用户对某次回答的 helpful / bad case 标记、原因和期望答案。
3. RAG evaluation dataset 保存标准问题、期望关键词和期望答案，用来检查标准问题是否被问过、是否召回了上下文。

## 后续指标

当前版本主要展示覆盖率和 bad case 反馈。后续可以继续补：

- hit rate：期望文档或 chunk 是否被召回。
- MRR：正确结果在召回列表中的排名是否足够靠前。
- bad case rate：用户标记为坏例子的比例。

## 面试表达

我没有只看模型有没有输出，而是通过问答日志、bad case feedback 和 evaluation dataset 建立质量闭环。后续可以基于标准问题继续补 hit rate、MRR 等离线评估指标。
