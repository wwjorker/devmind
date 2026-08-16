# Phase B Tool Calling design review — 2026-08-17

## Invocation record

- Purpose: read-only design review before the first high-risk Phase B slice.
- Reviewed snapshot: Git-tracked files at bddd93a.
- Requested and actual primary model: claude-opus-4-8.
- Claude CLI also reported a small amount of internal
  claude-haiku-4-5-20251001 usage.
- CLI API-equivalent cost metadata: USD 0.87911725. The CLI was authenticated
  through the user's Claude subscription, so this value is retained as usage
  metadata and is not represented as an additional API charge.
- Tools allowed to the reviewer: Read, Glob, and Grep only.
- Excluded material: .idea, environment variables, credentials, API keys,
  tokens, personal contact data, and unrelated career documents.
- The temporary git archive snapshot was deleted after the response.

This is the first of at most two Claude reviews allowed for Phase B. It is a
supplemental Claude 4.8 review, not an "Opus 5" review.

## Prompt

> You are a read-only architecture and protocol auditor for the DevMind Phase B
> design. Review only the Git-tracked snapshot in the current directory. Do not
> edit files, run Bash, access parent/outside paths, inspect credentials, use
> network tools, or propose scope expansion.
>
> Project context: Java 17, Spring Boot 3.3.5. Phase A is complete. Phase B must
> first prove a minimal OpenAI-compatible/DeepSeek Tool Calling protocol before
> agent persistence and orchestration. Existing v1 LlmClient/DeepSeekLlmClient
> must remain unchanged.
>
> Proposed single-commit scope:
>
> 1. Add a new AgentModelClient abstraction representing exactly one
>    chat-completion round, not an agent loop.
> 2. Define immutable protocol records for messages, function tool definitions,
>    tool calls, request, response, finish reason, and token usage. Use JsonNode
>    for JSON Schema. Preserve tool-call arguments as the provider's raw JSON
>    string; parsing, validation, and execution belong to a later tool layer.
> 3. Retain exact assistant tool calls and tool-call IDs for stateless
>    multi-round reconstruction.
> 4. Add a DeepSeek client using existing configuration and RestClient.Builder;
>    send messages, tools, tool choice, and an explicit disabled-thinking
>    setting; allow nullable assistant content; reject malformed envelopes; and
>    perform no execution or retries.
> 5. Add a deterministic, non-Spring-singleton scripted client.
> 6. Add HTTP fixture tests for both rounds, missing configuration, malformed
>    responses, plus an opt-in real-provider smoke test.
> 7. Do not add persistence, tools, orchestration, document versions,
>    frameworks, public APIs, or changes to the v1 client.
>
> Decide whether this is minimal but sufficient, enumerate exact wire fields and
> invariants, assess disabled-thinking encoding, identify executable risks and
> minimal corrections, reuse repository conventions, and return PASS,
> PASS_WITH_FIXES, or BLOCK.

## Claude conclusion summary

Claude returned PASS_WITH_FIXES. It accepted the one-round client boundary and
the exclusions. Its blocking-if-shipped findings were all protocol-local:

1. pin snake_case wire names (tool_calls, tool_call_id, finish_reason);
2. allow content: null for tool-call assistant messages;
3. preserve function.arguments as a string instead of parsing and re-emitting
   it as an object;
4. clone the shared RestClient.Builder before applying authorization headers;
5. avoid Map.of for nullable assistant content.

It also recommended exact raw-JSON fixtures for second-round reconstruction,
missing-key and malformed-envelope tests, a per-run scripted client, and reuse of
existing DeepSeek configuration and timeout customization.

Claude questioned relying on an unverified thinking request flag and recommended
model selection as the primary guarantee.

## Codex adjudication

- **Accepted:** the five wire-fidelity findings. The implementation uses
  controlled LinkedHashMap serialization, raw argument strings, nullable
  content, exact tool-call IDs, and a cloned RestClient builder.
- **Accepted:** one model call per client invocation. Tool execution, retries,
  budgets, persistence, and loop control remain outside this commit.
- **Accepted:** a deterministic ScriptedAgentModelClient that is not a Spring
  singleton and fails clearly on script exhaustion.
- **Accepted:** strict request fixtures for first- and second-round messages,
  including two tool calls and matched tool results.
- **Partially accepted:** malformed argument JSON is preserved by the transport,
  but later schema validation must reject it before tool execution.
- **Rejected:** treating the configured model name alone as proof that thinking
  is disabled. The current official DeepSeek chat-completion contract documents
  thinking: {"type":"disabled"}. The implementation encodes that field
  explicitly and fixture-tests it. It intentionally ignores any provider
  reasoning field rather than adding it to message history.
- **Deferred:** real-provider compatibility is represented by an opt-in,
  credential-safe smoke test. It was not run during this slice because neither
  DEVMIND_DEEPSEEK_API_KEY nor the explicit smoke-test switch was present.

No Claude suggestion expanded the phase scope.
