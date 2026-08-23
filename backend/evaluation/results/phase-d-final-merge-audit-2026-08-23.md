# Phase D final merge audit — 2026-08-23

## Decision

No confirmed code, migration, test, secret-handling, or build blocker remains in
the audited snapshot. `feat/multi-agent-v2` is technically ready for independent
acceptance and a deliberate merge into `main`. This record does not merge or
push the branch.

The result does **not** support a resume claim that Multi-Agent review is better
than the simpler model arms. The scored provider run closed that gate. The
strongest honest claim is that DevMind implements and tests a controlled,
auditable repair workflow and publishes both positive and negative evaluation
results.

## Audited snapshot

- Base branch: `main`
- Base and merge-base: `e9865be8173f9fd4d6f24aa1b871339e8a0bd4f2`
- Base tag: `v1.0`
- Audited head: `1a59914befdac0a349b14e36b93731d5607d9940`
- Feature branch: `feat/multi-agent-v2`
- Distance from base: 34 commits
- Diff from base: 190 files, 20,272 insertions, 565 deletions

The merge-base exactly equals current `main`; no unrelated mainline divergence
was hidden during the audit.

## Merge gates

### Backend

Command, from `backend/`:

```powershell
$env:JAVA_HOME='F:\IntelliJ IDEA 2024.1.2\jbr'
$env:DOCKER_HOST='npipe:////./pipe/dockerDesktopLinuxEngine'
.\mvnw.cmd "-Dapi.version=1.44" test
```

Result:

- Java `17.0.11`, Spring Boot `3.3.5`
- 193 tests, 0 failures, 0 errors, 3 skips
- MySQL 8.0 Testcontainers applied Flyway V1 through V13 from an empty schema
- `DevMindMySqlIntegrationTest`: 5/5 passed
- pgvector PostgreSQL 16 integration: 5/5 passed
- transaction-boundary, recovery, compensation, tool protocol, controller,
  security, retrieval, and document-version suites passed

The three skipped tests are explicit opt-in checks, not environment failures:

- real DeepSeek tool-calling smoke (a successful real-provider run is preserved)
- real four-arm provider evaluation (the 240-call scored run is preserved)
- pgvector brute-force/HNSW benchmark (the functional pgvector integration ran)

Expected warning stack traces from tests that inject Redis, provider, rollback,
and derived-index failures were not test failures.

### Frontend

`npm ci` completed from the lockfile with 48 packages and 0 reported
vulnerabilities during this audit cycle. The final command was:

```powershell
npm run build
```

`vue-tsc -b && vite build` passed under Vite `7.3.5`; 15 modules were transformed
and production assets were emitted.

## Evaluation evidence

- Real four-arm report: 240/240 provider calls completed. The independent
  Reviewer caught 10/12 defects versus self-review's 8/12, an uplift of 2 below
  the preregistered threshold of 5. `resumeClaimAllowed=false`.
- Controlled repair report: the one deterministic approved `METADATA_PATCH`
  fixture reached `APPLIED`, advanced document version 1 to 2, and passed target
  retrieval. `targetRepairRate=1/1`; this is not generalized.
- Frozen 40-case retrieval before/after: sparse hybrid Hit@3 remained `1.0000`,
  MRR remained `0.9381`, and both regression deltas were `0.0`. The keyword
  baseline also remained unchanged.
- The controlled run used 14 persisted local sparse vectors before and after;
  it did not depend on an external model, remote embedding/rerank, Redis, or
  pgvector for the measured arms.

## Repository and secret checks

- No `.idea`, `.env`, private key/certificate, screenshot, clipboard, or archive
  path is present in the 190 changed tracked files.
- A non-printing scan of the full changed content found no API-key, bearer-token,
  private-key, or AWS-key pattern.
- `.idea` remained ignored local state and its contents were not read.
- Flyway versions V1 through V13 are unique and contiguous.
- Repair routes remain behind the authenticated default security rule and use
  the authenticated principal's user ID.
- No remote push was performed.

## Remaining limitations, not hidden blockers

- The evaluation sets are small, internal, and non-statistical. They demonstrate
  reproducibility and failure-aware reporting, not general model superiority.
- Only one deterministic controlled repair fixture is scored end to end.
- The independent Reviewer failed its declared uplift gate; documentation and
  resume language must retain that negative result.
- The performance benchmark remains opt-in. Functional pgvector behavior is
  covered, but a new performance claim would require a separately frozen run.

No additional Claude or Fable call was made for this audit. Phase D had already
used its two allowed Claude Opus 4.8 read-only reviews; adding another review
would violate the agreed cap without producing stronger executable evidence.
