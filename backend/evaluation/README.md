# DevMind evaluation assets

`src/main/resources/evaluation/v1-retrieval-cases.json` is the versioned 40-case
retrieval regression dataset used by the existing runtime evaluation endpoints.
The application loads and validates it at startup; case IDs, questions, and gold
document titles are no longer embedded in production Java rules.

`v2-preregistered-protocol.json` freezes the Multi-Agent comparison before Agent
implementation. It defines dataset sizes, four experiment arms, fairness budgets,
metrics, safety gates, and the threshold for claiming that the independent
Reviewer adds value.

`v2-development-bad-cases-v0.1.json` contains six label-visible development
cases: one technical case for each preregistered root-cause label. They exercise
the scripted Phase B orchestration path and are not an accuracy result.

`v2-development-bad-cases-v1.json` completes the preregistered development set
with 12 label-visible cases, two for each root cause. It is for prompt and
workflow development only and must not be mixed into holdout scores.

`v2-sealed-bad-cases-v1.json` freezes 24 holdout cases, four for each of the six
root causes. Gold labels remain in this offline evaluation artifact, but the
runtime adapter must strip every gold field before constructing model messages.
The file is frozen before the first scored provider run; it is not claimed to be
model-unseen merely because it is named sealed.

`v2-reviewer-challenges-v1.json` freezes 24 reviewer cases: 12 valid proposals
and 12 defective proposals, with three examples for each preregistered defect
class. It measures whether an independent Reviewer catches material defects
without rejecting sound proposals.

`v2-sealed-bad-cases-v2.json` and `v2-reviewer-challenges-v2.json` are fresh
2.0.0 datasets frozen after the reviewer-prompt amendment and before its first
provider run. They keep the preregistered class allocation but use new case IDs
and wording. Version 1 remains unchanged and its corrected rerun, if any, is
post-hoc regression evidence only.

`v2-run-manifest-template.json` records the four preregistered arms (`rules`,
`single`, `single+self-review`, and `reviewed-multi`), common budgets,
provider/model identity, dataset hashes, run counts, metrics, and the claim gate.
Metric and hash fields intentionally remain null until a real run is performed;
null is evidence that Phase C did not fabricate evaluation results.

Before any scored run, stable IDs, the knowledge snapshot, exact model, and
SHA-256 hashes must be recorded in a run manifest. Frozen data is never edited in
place; changes require a new dataset version and a separately reported run.

Phase C execution gates the changed proposal with its target question after the
derived index is rebuilt. The existing 40-case retrieval suite remains the
separate full regression report. A before/after global delta is not synthesized
inside one repair execution because no comparable pre-change baseline is stored
there; Phase D must run and record both sides under the frozen manifest.

## Phase D reproducible checks

The first opt-in real-provider protocol check is recorded in
`results/phase-d-deepseek-tool-calling-smoke-2026-08-23.md`. It proves the
two-round tool-calling wire path against the configured DeepSeek model, but is
not a scored four-arm evaluation or a model-quality claim.

The first complete four-arm run is preserved in
`results/phase-d-four-arm-provider-run-2026-08-23-invalid.json` with its
human-readable incident record beside it. It completed 240 provider calls but
the original `reviewed-multi` prompt collapsed to an all-accept classifier. The
run is invalid for quality comparison and its closed claim gate must not be
reinterpreted. `v2-protocol-amendment-2026-08-23.json` records the wording-only
protocol repair, unchanged fairness contract, new degeneracy diagnostics and
evidence policy. Any corrected v1 rerun is post-hoc regression evidence; a fresh
dataset version is required for another scored result.

The first scored amended run is preserved in
`results/phase-d-four-arm-provider-run-v2-2026-08-23.json`, with a concise
decision record beside it. It used the fresh 2.0.0 datasets, the exact
`deepseek-v4-flash` model at temperature zero, and completed all 240 model calls
without a provider failure. All three model arms classified 24/24 root-cause
cases correctly. On reviewer challenges, `single`, `single+self-review`, and
`reviewed-multi` caught 12/12, 8/12, and 10/12 defective proposals respectively.
The independent reviewer therefore added two catches over self-review, below the
preregistered threshold of five; `resumeClaimAllowed` is false. This is a scored
negative result, not evidence of Reviewer uplift.

The real MySQL merge gate uses the normal Testcontainers test. Docker Engine 29
raises its minimum client API above the docker-java default used here, so pass an
explicit compatible API version instead of editing a developer's private Docker
or Testcontainers configuration:

```powershell
$env:JAVA_HOME='C:\path\to\jdk-17'
$env:DOCKER_HOST='npipe:////./pipe/dockerDesktopLinuxEngine'
.\mvnw.cmd "-Dapi.version=1.44" "-Dtest=DevMindMySqlIntegrationTest" test
```

The same integration test also writes
`target/evaluation/phase-d-controlled-repair-report.json`. It seeds the fixed
offline demo, persists local sparse vectors for every active chunk before the
baseline, evaluates all 40 frozen retrieval cases, executes the single seeded
and human-approved metadata repair, and evaluates the same cases again. This
keeps the MySQL snapshot, case order, embedding provider, K, and retrieval limit
comparable on both sides. The measured `targetRepairRate` is deliberately scoped
to one deterministic repair fixture and must not be presented as a generalized
success rate. Set `DEVMIND_EVAL_KNOWLEDGE_SNAPSHOT` when preserving a report;
ordinary test runs use the explicit placeholder `git:working-tree`.

`V2DeepSeekProviderEvaluationTest` is the opt-in four-arm runner. It reads the
2.0.0 frozen files, hashes their exact bytes, strips gold fields from model messages,
uses temperature zero, counts invalid/provider failures as failed cases, and
writes only to `target/evaluation/v2-four-arm-provider-report.json`. A full run
attempts at most 240 model calls (48 single, 96 self-review, 96 reviewed-multi),
so estimate cost before enabling it.

This runner is deliberately an offline prompt-only comparison over root-cause
classification and reviewer challenges. It gives every arm an empty tool set
and does not execute the production orchestrator, evidence tools, approval, or
repair loop. Target repair and retrieval regression remain separate integration
evidence, so this report must not be presented as an end-to-end Agent score.

The test remains skipped unless all of the following are set:

- `DEVMIND_RUN_V2_EVALUATION=true`
- `DEVMIND_DEEPSEEK_API_KEY`
- `DEVMIND_DEEPSEEK_MODEL` (an exact frozen model name, not chosen per arm)
- `DEVMIND_EVAL_KNOWLEDGE_SNAPSHOT`
- `DEVMIND_DEEPSEEK_INPUT_USD_PER_MILLION`
- `DEVMIND_DEEPSEEK_OUTPUT_USD_PER_MILLION`
- `DEVMIND_EVAL_PRICING_BASIS` (name the list-price source and any endpoint
  mismatch or estimation limitation; do not present an estimate as a bill)

Run it with:

```powershell
.\mvnw.cmd "-Dtest=V2DeepSeekProviderEvaluationTest" test
```

The v1 frozen text is deliberately retained even though its rules baseline is
keyword-tuned to the exact wording and reaches a ceiling score. The fresh v2
wording exposed that limitation: the same rules classified only 6/24 root-cause
cases, while the model arms reached 24/24. Neither observation establishes a
general Agent advantage on this small internal dataset. Any harder dataset must
receive a new version; prior files and reports must not be rewritten. The
report's component gate booleans are diagnostic only: `resumeClaimAllowed`
remains false because the Reviewer uplift threshold failed and this offline
prompt-only run cannot establish end-to-end production quality.
