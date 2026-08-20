# Phase B reverse review — 2026-08-20

## Invocation record

- Purpose: second and final read-only Claude review for the complete Phase B slice.
- Reviewed snapshot: 237 Git-tracked files at d488a23, covering the Phase B range
  bddd93a..d488a23.
- Requested model alias: `opus`, with no fallback configured.
- Actual primary model reported by Claude CLI: `claude-opus-4-8`.
- Claude CLI also reported 1,342 input tokens and 19 output tokens of internal
  `claude-haiku-4-5-20251001` usage.
- Primary-model usage reported by the CLI: 3,750 input tokens, 24,228 output
  tokens, 104,205 cache-creation input tokens, and 1,006,168 cache-read input
  tokens.
- CLI API-equivalent cost metadata: USD 1.78025225. The CLI was authenticated
  through the user's Claude subscription, so this value is retained as usage
  metadata and is not represented as an additional API charge.
- Tools allowed to the reviewer: Read, Glob, and Grep only. Bash, edits, writes,
  notebooks, and web access were disabled.
- Excluded material: `.idea`, environment variables, credentials, API keys,
  tokens, personal contact data, and unrelated career documents.
- The temporary Git archive and its extracted snapshot were deleted after the
  response. The snapshot file count matched `git ls-files`, and no `.idea`
  directory was present.

This was the second of at most two Claude reviews allowed for Phase B. It was a
supplemental Claude 4.8 review, not an "Opus 5" review. No further Claude call
was made while resolving its findings.

## Prompt

> You are the second and final read-only reverse auditor for DevMind Phase B.
> Review only this Git-tracked snapshot. Do not edit or create files, run Bash,
> access parent/outside paths, inspect credentials, use network tools, or
> propose scope expansion. Use only Read, Glob, and Grep.
>
> Context: Java 17, Spring Boot 3.3.5. Phase A ended at bddd93a. Phase B consists
> of these four commits: 8209f3a tool-calling model protocol; 7ca1f7b durable
> run/step budgets; 40c6c5a audited triage read tools; d488a23 bounded evidence
> triage orchestration.
>
> Phase B is a minimal, explainable EvidenceTriage technical slice, not a public
> product workflow. Required scope: a one-round AgentModelClient and scripted
> client plus opt-in DeepSeek smoke; durable agent_run/agent_step records with
> hard limits; exactly three read-only tools and a strict six-label diagnosis
> protocol; a bounded internal loop; and the first six label-visible development
> cases as scripted orchestration smoke. Bad-case intake/state machine, Reviewer,
> write tools, HITL, document versions, public API, and accuracy claims remain
> out of scope until Phase C.
>
> The frozen fairness contract is at most 6 model calls, 12 tool calls, 24,000
> total tokens, and 120 seconds. The first action must read the target ask log.
> Tool output and expected answers are untrusted. Evidence references must bind
> to executed tool-call IDs and returned owned IDs. Old or unknown prompt schemas
> cannot support ANSWER_WRONG_WITH_CORRECT_EVIDENCE. MySQL is the source of truth.
>
> Codex verification before review: 9 focused tests and 145 full-suite tests,
> both with zero failures/errors; eight environment-gated tests were skipped.
> A clean JDK 17 run and actual MySQL V5/V6/V7 validation remain merge gates.
>
> Audit wire fidelity and conversation reconstruction; transaction, concurrency,
> idempotency, ownership and terminal states; hard budgets; strict/read-only
> tools and audit safety; evidence binding and protocol labels; honesty of the
> six development cases; static MySQL migration compatibility; and Phase B scope
> discipline. Return PASS, PASS_WITH_FIXES, or BLOCK; prioritized reproducible
> findings with the smallest correction/test; confirmed strengths; deferred
> gates and Phase C items; and an explicit statement if no blocker exists. Do
> not ask questions or implement anything.

## Claude conclusion summary

Claude returned `PASS_WITH_FIXES` and explicitly found no P0/blocking defect.
It confirmed wire reconstruction, row-lock-based model-call limits, ownership
checks, terminal transitions, evidence binding, prompt-schema gating, read-only
tool boundaries, six-label routing, honest scripted-case claims, static V5-V7
MySQL syntax, and Phase B scope discipline.

Its concrete findings were:

1. P1: a model call could cross `maxTotalTokens`, after which the run could still
   be marked `SUCCEEDED`; it also suggested sending provider `max_tokens`.
2. P2: the 12-tool limit was enforced only in the in-memory triage loop, not as
   a durable run counter.
3. P2: concurrent creation with the same idempotency key could catch a duplicate
   insert and then use a repeatable-read snapshot query that misses the winner.
4. P2: the deadline is checked at step boundaries, so a blocking HTTP call may
   finish after the wall-clock deadline before the run is marked timed out.
5. P3: stricter-than-generic DeepSeek parsing and the provider-specific
   `thinking` field are portability constraints rather than current DeepSeek
   defects.

It suggested negative tests for cross-tenant reads, a non-target ask-log result,
token/tool budgets, and concurrent idempotent creation. It retained clean JDK 17,
real MySQL migration, real DeepSeek, and pgvector runs as environment gates.

## Codex adjudication and changes

- **Accepted:** token overflow must never reach success. `completeModelStep` now
  records the completed step and actual usage, then moves the run immediately to
  `BUDGET_EXHAUSTED` when total usage is greater than the configured maximum.
  Equality remains valid because the contract says "at most". Agent responses
  must now contain measurable, non-negative usage; otherwise the provider call
  fails instead of silently consuming zero tokens.
- **Partially accepted:** no `max_tokens` wire field was added. That field caps
  completion tokens only and cannot guarantee the combined prompt-plus-completion
  total. The reproducible invariant for this slice is that an over-budget call is
  audited and counted as a failed case, never a successful one. Per-request
  remaining-budget output caps may be added later only with a provider-neutral
  request contract and prompt-size accounting.
- **Accepted:** V8 adds `max_tool_calls` and `used_tool_calls`. Tool reservations
  increment and check these under the existing owned run row lock. The local
  12-call orchestration guard remains as an earlier batch-level defense.
- **Accepted:** duplicate-insert recovery now performs a locking current read by
  `(user_id, idempotency_key)`, avoiding a stale repeatable-read snapshot. A unit
  regression test verifies this recovery path. A real two-connection MySQL race
  remains part of the actual-MySQL merge gate.
- **Deferred:** dynamic cancellation of an in-flight blocking HTTP request. The
  run is still recorded as `TIMED_OUT` when the call returns, and the configured
  HTTP read timeout bounds the overrun. Strict remaining-deadline propagation is
  not required for the internal Phase B slice.
- **Recorded, no change:** DeepSeek's documented envelope and disabled-thinking
  field are intentionally provider-specific inside `DeepSeekAgentModelClient`.
- **Already covered:** the real read-tool integration test filters foreign-user
  chunks and rejects a foreign ask log. No duplicate test was added.
- **Accepted:** a new orchestration negative test rejects a successful tool
  result for an ask log other than the requested target.

The V8 migration initially exposed an H2 compatibility issue because one ALTER
statement added two columns. It was split into two standard ALTER statements;
the production migration then passed the H2 MySQL-mode integration tests. This
is not represented as real MySQL validation.

## Verification after adjudication

- Focused budget/model/orchestration suite: 27 tests, 0 failures, 0 errors,
  0 skipped.
- Full backend suite: 151 tests, 0 failures, 0 errors, 8 environment-gated skips.
- V8 was exercised in H2 MySQL mode by the persistence, tool-executor, and triage
  integration tests.
- Docker/Testcontainers remained unavailable, so actual MySQL V5-V8 Flyway
  validation and pgvector integration were not run.
- The opt-in real DeepSeek smoke test was not run because its explicit provider
  environment gate was absent.
- JDK 19 continued to emit its known first-compilation "unable to close compiler
  resources" failure; the next invocation with current class files completed all
  tests. A clean JDK 17 build remains a merge gate.

No Claude suggestion expanded Phase B into Phase C.
