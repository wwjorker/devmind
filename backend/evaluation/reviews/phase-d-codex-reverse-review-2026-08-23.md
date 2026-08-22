# Phase D Codex reverse review — 2026-08-23

## Scope and method

This review covers the Phase D commits after Phase C (`ca018be..d214ce1`) plus
the corrections made while closing this review. Codex inspected the production
workflow, demo seed, UI, frozen four-arm runner, integration tests, documentation,
and secret-handling boundaries. Findings were accepted only after checking the
real code path and adding or strengthening reproducible tests.

Claude was not invoked. The locally available Claude CLI had previously reported
`claude-opus-4-8`, while the project rule permits a phase review only with the
exact Claude Opus 5 model and forbids fallback. Substituting another model would
create misleading provenance and could incur unexpected API usage. Fable 5 was
also not invoked because the user did not authorize a new paid review.

## Confirmed findings and disposition

1. **Demo evidence did not always match its source chunk — fixed.** The conflict
   fixture referenced a cache-penetration chunk while presenting distributed-lock
   content, and the metadata-repair excerpt was not an exact substring of the
   stored UTF-8 chunk. The seed now binds the actual lock chunk/document and uses
   source-backed excerpts. The MySQL test resolves each seeded snapshot back to
   `knowledge_document_chunk` and verifies the excerpt is present.

2. **The demo SQL test depended on the host default encoding — fixed.** JBR 17 on
   this Windows host reports GBK by default, while the SQL file is UTF-8. The test
   now sets the `ResourceDatabasePopulator` script encoding explicitly to UTF-8
   and asserts Chinese version/evidence content after seeding.

3. **Review-run idempotency was bound only to a bad case — fixed.** A client key
   reused for two proposals under the same bad case could return a succeeded run
   for the wrong proposal. The internal key now includes `proposalId`, with a
   regression test that verifies the bound key.

4. **The UI shared one edited-diff buffer across all proposals — fixed.** Opening
   a case with multiple proposals could submit another proposal's JSON. Edit state
   is now keyed by proposal ID. The demo password is no longer prefilled.

5. **The four-arm runner could be mistaken for end-to-end Agent evaluation —
   fixed by explicit disclosure.** The generated report identifies the mode as
   offline prompt-only classification/review, and the evaluation README states
   that it does not run tools, orchestration, approval, or repair. Production
   workflow and retrieval evidence remain separate gates.

6. **Frontend lockfile contained two known vulnerable transitive versions —
   fixed.** `dompurify` and `nanoid` were updated within their existing compatible
   dependency ranges; no package was added and no forced major upgrade was used.

7. **The backend README named the wrong MySQL major version — fixed.** The
   Testcontainers gate and Compose configuration use MySQL 8.0, not 5.7.

## Validation evidence

- Backend full suite with Docker unavailable: 190 tests, 0 failures, 0 errors,
  12 environment-gated skips. The final Docker-enabled run executed 191 tests
  with 0 failures, 0 errors, and 3 explicit skips: the private-provider smoke,
  the opt-in 240-call evaluation, and the standalone pgvector benchmark.
- Real MySQL 8.0 Testcontainers gate after the encoding/evidence corrections:
  Flyway V1 through V13 applied; 4 tests passed. The full suite also executed the
  5-test pgvector integration class against a real PostgreSQL/pgvector container.
- Focused workflow/evaluation regression: 5 tests passed.
- Frontend clean install audit after the lockfile update: 0 vulnerabilities.
- Frontend TypeScript/Vite production build: passed.
- Real DeepSeek two-round tool-calling smoke: 1 test passed; the separately
  recorded result proves provider wire compatibility, not model quality.

Final reruns are recorded in the Phase D handoff; this document does not turn a
skipped environment-gated test into a pass.

## Rejected scope expansion

- No Outbox, message queue, Vault deployment, third Agent, supervisor hierarchy,
  MCP server, or evaluator rewrite was added. None was required to close a
  confirmed Phase D correctness gap.
- The frozen v1 rules baseline was not weakened to make Multi-Agent results look
  better. Its ceiling remains a disclosed dataset limitation.

## Remaining limitations and claim boundary

- The 240-call real-provider four-arm run has not been executed in this Codex
  process because its private credentials are intentionally confined to the
  developer's IDEA configuration. No report or score may be fabricated.
- Even after that run, its result is an offline prompt-only comparison. It must be
  presented together with, but not merged into, the separate MySQL recovery,
  retrieval-regression, approval, compensation, and demo evidence.
- A full provider report remains a pre-merge evidence item if the resume or demo
  intends to claim measured Reviewer value. Otherwise the claim must remain that
  the runner and protocol are reproducible, not that one arm won.
