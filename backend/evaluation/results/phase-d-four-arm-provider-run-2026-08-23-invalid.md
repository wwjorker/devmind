# Phase D four-arm provider run — invalid protocol run

This report is preserved as failure evidence and must not be used as a model-
quality or resume claim. It was the first complete provider run and exposed a
degenerate `reviewed-multi` prompt. The raw report is
`phase-d-four-arm-provider-run-2026-08-23-invalid.json` (SHA-256
`336ac916ceacbc2c50397c86f567b4b29610e0628323b9d6528329a6d10b7d42`).

## Frozen inputs and provider

- Started: `2026-08-22T23:57:53.946077300Z`
- Provider endpoint: `https://api.deepseek.com`
- Model: `deepseek-v4-flash`
- Temperature: `0`
- Knowledge snapshot: `git:c17000b,datasets:v1`
- Sealed cases SHA-256: `d4eac411856239d1c9304e1bd45b386a68f53d94ca274701b53338ae12c3dfb2`
- Reviewer challenges SHA-256: `44e47ab237849e0aee26b23655f2409bcaa77f66664b4b68b8a7e828fa43b684`
- Legacy retrieval SHA-256: `5e22a59a3548ef71131994d92c9eb5ad52bfad6c91ffd5c89e74fb37b725a8c8`

The pricing string included an irrelevant BigModel limitation based on the
pre-run endpoint assumption. The report itself proves that the request used the
official DeepSeek endpoint. Costs remain list-price estimates, not billing
records.

## Observed results

| Arm | Root correct | Valid accepted | Defects caught | Safety blocked | Calls | Failed | Tokens | P50 ms | P95 ms | Estimated USD |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| rules | 24/24 | 12/12 | 12/12 | 6/6 | 0 | 0 | 0 | 0 | 0 | 0 |
| single | 24/24 | 12/12 | 12/12 | 6/6 | 48 | 0 | 11,125 | 879 | 1,334 | 0.00165816 |
| single+self-review | 24/24 | 12/12 | 12/12 | 6/6 | 96 | 0 | 24,599 | 854 | 1,132 | 0.00364504 |
| reviewed-multi | 24/24 | 12/12 | 0/12 | 0/6 | 96 | 0 | 24,675 | 1,277 | 1,671 | 0.00364280 |

All 240 model calls completed without provider or parse failures. Nevertheless,
`reviewed-multi` returned `acceptable=true` for all 24 mixed challenge cases.
This is a constant classifier, so the report is invalid for comparing reviewer
quality. Its claim gate also remained closed (`additionalDefectsCaught=-12`,
`resumeClaimAllowed=false`).

## Root cause and disposition

The independent-review prompt described `candidateJson` as untrusted and not
authoritative but did not explicitly redirect the reviewer to the original
proposal or permit agreement. The observed all-accept output tracks that one
wording difference; the otherwise identical self-review path remained 12/12.

The amendment in `../v2-protocol-amendment-2026-08-23.json` corrects only this
task framing, preserves second-output-wins semantics, adds a confusion matrix
and a degenerate-arm validity check, and requires a fresh dataset version for a
new scored claim. A v1 rerun after the fix is regression evidence only.
