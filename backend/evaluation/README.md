# DevMind evaluation assets

`src/main/resources/evaluation/v1-retrieval-cases.json` is the versioned 40-case
retrieval regression dataset used by the existing runtime evaluation endpoints.
The application loads and validates it at startup; case IDs, questions, and gold
document titles are no longer embedded in production Java rules.

`v2-preregistered-protocol.json` freezes the Multi-Agent comparison before Agent
implementation. It defines dataset sizes, four experiment arms, fairness budgets,
metrics, safety gates, and the threshold for claiming that the independent
Reviewer adds value.

The v2 development, sealed, and challenge case files do not exist yet. This
protocol does not claim otherwise. When those files are created, their stable IDs,
knowledge snapshot, exact model, and SHA-256 hashes must be recorded before the
first scored sealed run. Frozen data is never edited in place; changes require a
new dataset version and a separately reported run.
