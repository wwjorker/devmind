# Phase D four-arm provider run — amended v2 dataset

This is the first scored provider run after the independent-review protocol
amendment. The raw report is
`phase-d-four-arm-provider-run-v2-2026-08-23.json` (SHA-256
`290db07d1326562c78a85d39158c36e0aba0f5c66f3604a32037ebffde47a51d`).
The earlier v1 run remains preserved separately as invalid protocol evidence
and was not overwritten.

## Frozen inputs and provider

- Started: `2026-08-23T00:25:55.1232660Z`
- Provider endpoint: `https://api.deepseek.com`
- Model: `deepseek-v4-flash`
- Temperature: `0`
- Knowledge snapshot: `git:516b07b,datasets:v2`
- Protocol: `1.0.0+amendment-2026-08-23`
- Sealed cases: version `2.0.0`, SHA-256 `b6dec8267b2a518f5b40206c309412f6a48b4b632b4db242b849a40ed251df0f`
- Reviewer challenges: version `2.0.0`, SHA-256 `7de4152bb36d83c41c0a429455e69d7a181464c5672a1fc0b182d3644c3462f3`
- Legacy retrieval set: version `1.0.0`, SHA-256 `5e22a59a3548ef71131994d92c9eb5ad52bfad6c91ffd5c89e74fb37b725a8c8`
- Pricing: official DeepSeek direct cache-miss list price recorded on
  `2026-08-23`; cost values are estimates, not billing records

The runner classified the run as `scored-provider-run`. Its validity guard
passed because every model arm discriminated between valid and defective
challenge proposals. This only establishes that the protocol produced a
scoreable run; it does not establish a product-quality or resume claim.

## Observed results

| Arm | Root correct | Valid accepted | Defects caught | Safety blocked | Unscored | Calls | Failed | Tokens | P50 ms | P95 ms | Estimated USD |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| rules | 6/24 | 12/12 | 0/12 | 0/6 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| single | 24/24 | 11/12 | 12/12 | 6/6 | 0 | 48 | 0 | 11,561 | 815 | 1,192 | 0.00171990 |
| single+self-review | 24/24 | 11/12 | 8/12 | 3/6 | 0 | 96 | 0 | 25,445 | 874 | 1,115 | 0.00376012 |
| reviewed-multi | 24/24 | 11/12 | 10/12 | 5/6 | 1 | 96 | 0 | 26,980 | 820 | 1,060 | 0.00397852 |

Across the three model arms, all 240 provider calls completed with zero model
call failures. They consumed 63,986 reported tokens at an estimated total
list-price cost of USD 0.00945854. The reviewed-multi arm had one unscored
challenge response because the returned fields were internally inconsistent;
the raw report preserves the exact case-level outcome.

## Claim-gate decision

The independent-review arm caught 10/12 defects, two more than the self-review
arm's 8/12. The predeclared threshold required at least five additional
defects. Therefore:

- `additionalDefectsCaught = 2`
- `requiredAdditionalDefectsCaught = 5`
- `additionalDefectsSatisfied = false`
- `costRatio = 1.0581`, within the cost gate
- `resumeClaimAllowed = false`

The strongest prompt-only baseline in this run was the single-model arm, which
caught 12/12 challenge defects. The independent-review arm did not beat that
baseline, and self-review reduced defect capture on this small dataset. These
results do not support a claim that adding more model passes automatically
improves quality.

End-to-end controlled-repair quality and frozen before/after retrieval
regression remain separate evidence requirements. They are deliberately not
inferred from this offline prompt-only run. The dataset is small and internal,
so no statistical generalization claim is made.
