# DevMind evaluation assets

`src/main/resources/evaluation/v1-retrieval-cases.json` is the versioned 40-case
retrieval regression dataset used by the existing runtime evaluation endpoints.
The application loads and validates it at startup; case IDs, questions, and gold
document titles are no longer embedded in production Java rules.

`v2-preregistered-protocol.json` freezes the Multi-Agent comparison before Agent
implementation. It defines dataset sizes, four experiment arms, fairness budgets,
metrics, safety gates, and the threshold for claiming that the independent
Reviewer adds value.

`v2-development-bad-cases-v0.1.json` contains the first six label-visible
development cases: one technical case for each preregistered root-cause label.
They exercise the scripted Phase B orchestration path and are not presented as
an accuracy or real-model result. The full 12-case development set, sealed set,
and Reviewer challenge set do not exist yet.

Before any scored run, stable IDs, the knowledge snapshot, exact model, and
SHA-256 hashes must be recorded in a run manifest. Frozen data is never edited in
place; changes require a new dataset version and a separately reported run.
