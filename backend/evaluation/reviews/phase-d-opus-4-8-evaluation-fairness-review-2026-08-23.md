# Phase D Opus 4.8 evaluation-fairness review

## Why it was called

The first complete four-arm provider run produced a high-risk fairness anomaly:
`reviewed-multi` accepted every reviewer challenge while all calls completed.
The standing authorization allows one targeted architecture review for four-arm
fairness. This was the second and final Claude call for Phase D.

## Materials provided

The prompt contained only:

- aggregate per-arm results (24 root cases and 24 reviewer challenges);
- the shared proposal-review system prompt;
- the self-review and independent-review suffixes;
- the fact that the second output replaces the first verdict;
- five bounded questions about cause, minimal correction, tests and evidence
  preservation.

No repository, `.idea` file, environment variable, API key, token, contact data
or unrelated career material was provided. Claude had no tools and made no file
changes.

## Invocation and usage

- CLI model: `claude-opus-4-8`
- Fallback: none
- Mode: print, safe mode, no session persistence, no tools
- Result: success
- Main-model equivalent cost: `$0.198270`
- Total equivalent cost: `$0.199456`
- Internal use: `claude-haiku-4-5-20251001` used 1,096 input and 18 output
  tokens; it was not selected as a fallback model.

## Reviewer conclusions (condensed from the raw response)

1. **Critical:** the result is a prompt/protocol defect, not credible evidence
   that independent review harms quality. An all-accept classifier is
   degenerate, and the only material difference from the successful self-review
   arm was the reviewer suffix.
2. **High:** describing the candidate as untrusted shifted the task from judging
   the original proposal to discounting the prior rejection. The pattern was
   discard-and-default-approve, not a pure inversion.
3. **Required:** direct the reviewer back to the original proposal, explicitly
   permit agreement, retain independence, and do not add a deterministic veto or
   merge because that would change the measured arm.
4. **High:** add confusion matrices and degenerate-output guards, rerun the full
   protocol, keep exact-count and small-sample caveats, and withhold resume claims.
5. **Medium-high:** preserve the failed report; overwriting it would be
   result-laundering and would erase the causal control.

## Codex adjudication

- **Accepted:** prompt framing is the proximate defect; the original proposal is
  now the explicit review target and agreement is allowed.
- **Accepted:** no mechanical merge/veto; all four arms and second-output-wins
  semantics remain unchanged.
- **Accepted:** add explicit confusion counts and automatically mark a mixed-set
  constant model arm as an invalid run.
- **Accepted with a stricter evidence rule:** rerunning v1 can prove the bug is
  fixed but is post-hoc because the labels/results have been observed. A new
  dataset version is required for fresh scored evidence.
- **Rejected as unnecessary:** archiving request/response bodies. They may carry
  source text and increase privacy and repository size; the existing report
  keeps case IDs, predictions, hashes, usage and failures, which is sufficient
  for this project-level reproducibility claim.
- **Confirmed:** the invalid raw report remains versioned and is never overwritten.
