# Phase A Claude read-only review — 2026-08-17

## Invocation record

- Purpose: independent read-only reverse review after Phase A.
- Compared snapshots: Git-tracked files from `e9865be` and `223f62f`.
- Requested CLI model: `--model opus`, with no configured fallback.
- Actual model reported by Claude CLI: `claude-opus-4-8`; the CLI also reported
  a small amount of `claude-haiku-4-5-20251001` internal usage.
- Cost reported by Claude CLI: USD 2.27997775.
- Tools allowed to the reviewer: `Read`, `Glob`, and `Grep` only.
- Excluded material: `.idea`, environment variables, credentials, API keys,
  tokens, personal contact data, and unrelated career documents.
- Temporary archives were deleted after the response was received.

The actual model identity means this result is retained as a supplemental Claude
review. It must not be represented as the requested "Opus 5" review.

## Prompt

> You are Claude Opus 5 acting only as a read-only architecture and code auditor
> for DevMind Phase A. Do not edit files, run commands, access paths outside the
> current audit directory, inspect credentials, or propose scope expansion.
>
> The directory contains two git-archive snapshots made only from tracked files:
> `before/` at baseline `e9865be` and `after/` at Phase A head `223f62f`.
>
> Phase A consists of four commits: prompt/preview/citation separation;
> embedding transaction-boundary repair; disabling remote on-the-fly embedding;
> and externalizing the v1 dataset plus preregistering the v2 protocol.
>
> Verify complete model input with bounded previews and matching citations;
> prompt-schema separation of legacy logs; the absence of remote embedding HTTP
> calls inside create/update/restore/backfill/retrieval transactions; MySQL as
> vector source data and pgvector as a rebuildable serving index; explainable
> external-call failures; keyword degradation without remote 512-chunk fallback;
> preservation of local sparse behavior; exact externalization of the 40-case v1
> dataset; and a fair, honest, testable four-arm v2 preregistration.
>
> Report PASS, PASS_WITH_FIXES, or BLOCK; list blocker/high/medium findings with
> executable scenarios and minimal corrections; explicitly verify every goal;
> separate merge gates from defects; and decide Phase B readiness. Do not invent
> findings or treat optional future Agent work as a blocker.

The prompt also supplied the observed test evidence: 112 tests, zero failures and
errors, seven Docker-gated skips; exact field-by-field equality of the old and new
40-case datasets; unverified real-MySQL V5 migration; and the local JDK 19 compiler
resource warning while the project targets Java 17.

## Claude conclusion summary

Claude returned `PASS` and reported no blocker, high, or medium defect. It
independently traced and accepted all five Phase A goals:

1. full prompt versus bounded preview, visible citations, and schema versioning;
2. suspended transactions around embeddings, short MySQL writes, and best-effort
   pgvector synchronization;
3. keyword-only degradation when remote vectors are absent, with local sparse
   fallback retained;
4. behavior-preserving v1 dataset externalization and honest v2 preregistration;
5. no public REST API or unrelated feature expansion.

It retained two merge gates: validate V5 against real MySQL and execute the seven
Docker/Testcontainers-gated tests. It also noted that
`PromptSchemaVersions.isAnswerGroundingEvaluationEligible` is not yet consumed by
an evaluator.

## Codex adjudication

- **Confirmed:** full prompt, preview, citations, and schema-version behavior are
  tied to the same visible chunk list and covered by regression tests.
- **Confirmed:** create, update, restore, backfill, and retrieval embedding calls
  are outside database transactions; MySQL mutations use bounded transactions,
  and serving-index failures leave committed keyword-searchable source data.
- **Confirmed:** remote missing-vector fallback makes zero embedding calls and
  local sparse behavior is unchanged.
- **Confirmed:** the JSON contains the same 40 case definitions field-for-field;
  production Java no longer contains case-specific rules; the preregistration
  does not claim that sealed/challenge files or results already exist.
- **Confirmed:** public REST mappings are unchanged.
- **Accepted as a non-blocking limitation:** the schema eligibility helper is
  currently unused because no answer-grounding evaluator exists yet. The current
  dataset endpoint measures coverage/retrieval and does not classify "correct
  evidence, wrong answer". When that evaluator is introduced, it must use the
  helper or an equivalent schema-version filter.
- **Accepted as merge gates:** real-MySQL V5 validation and Docker-gated tests.
- **Rejected as a Phase B prerequisite:** the reviewer phrased those merge gates
  as conditions for starting Phase B. The user explicitly allowed development to
  continue while retaining them as pre-merge checks.
- **No code changes required:** Codex found no executable defect in the review
  claims after returning to the real repository and rechecking the relevant call
  sites and tests.
