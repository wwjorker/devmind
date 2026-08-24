# Phase D controlled repair before/after run

This is the real-MySQL end-to-end evidence that is intentionally separate from
the prompt-only four-arm provider comparison. The raw report is
`phase-d-controlled-repair-run-2026-08-23.json` (SHA-256
`03ff6eba80272715552047ea02822fd584498e5d33bc04456cc02ad8f53e5d4a`).

## Frozen inputs and fairness controls

- Knowledge snapshot: `git:5fea5b3976deb886fc1a9a9838d81dd212f04fe6`
- Retrieval dataset: `devmind-retrieval-v1` version `1.0.0`, 40 cases (35 positive)
- Retrieval dataset SHA-256: `5e22a59a3548ef71131994d92c9eb5ad52bfad6c91ffd5c89e74fb37b725a8c8`
- Demo seed SHA-256: `4fe8daaab070b814c8c3f9234ef4094328bc532b7854a6eb02836f3a3d84ac03`
- Database: Testcontainers MySQL 8.0, migrated from an empty schema through V13
- Embedding path: persisted `local-sparse-vector`; all 14 active chunks were
  backfilled before the baseline and 14 active vectors remained after repair
- Retrieval settings: Hit@3 with retrieval limit 5, identical before and after
- External dependencies: no model, remote embedding, remote rerank, Redis, or
  pgvector dependency was used for the measured arms

## Observed result

| Measure | Before | After | Delta |
| --- | ---: | ---: | ---: |
| Sparse hybrid pass rate | 0.9000 | 0.9000 | 0.0000 |
| Sparse hybrid Hit@3 | 1.0000 | 1.0000 | 0.0000 |
| Sparse hybrid MRR | 0.9381 | 0.9381 | 0.0000 |
| Keyword baseline pass rate | 0.8250 | 0.8250 | 0.0000 |
| Keyword baseline Hit@3 | 0.8286 | 0.8286 | 0.0000 |
| Keyword baseline MRR | 0.8071 | 0.8071 | 0.0000 |

The seeded `METADATA_PATCH` moved the target document from version 1 to version
2, finished with proposal status `APPLIED`, and passed its target retrieval
check. This produces `targetRepairRate = 1/1 = 1.0` for the one controlled
fixture. It is evidence that the implemented approval, write, index rebuild,
verification, and finalization path works for this fixture; it is not a
generalized repair-success claim.

No case changed its first relevant rank, Hit@3 result, or ordered top-document
titles. The frozen full-suite regression delta is therefore Hit@3 `0.0` and MRR
`0.0`. This closes the previously null before/after evidence item without
retroactively modifying the separately scored four-arm provider report.

## Reproduction

```powershell
$env:JAVA_HOME='C:\path\to\jdk-17'
$env:DOCKER_HOST='npipe:////./pipe/dockerDesktopLinuxEngine'
$env:DEVMIND_EVAL_KNOWLEDGE_SNAPSHOT='git:5fea5b3976deb886fc1a9a9838d81dd212f04fe6'
.\mvnw.cmd "-Dapi.version=1.44" "-Dtest=DevMindMySqlIntegrationTest" test
```

The source report is written to
`target/evaluation/phase-d-controlled-repair-report.json`; preserving it under
`evaluation/results/` is a deliberate evidence-publication step.
