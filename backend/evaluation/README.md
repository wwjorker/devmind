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
