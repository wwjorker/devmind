# DeepSeek tool-calling smoke

> Historical experiment record from 2026-08-23; not rerun during documentation maintenance.

## Run identity

- Date: 2026-08-23 (Asia/Shanghai)
- Status: pass
- Provider: DeepSeek
- Exact configured model: `deepseek-v4-flash`
- Test: `DeepSeekAgentModelClientSmokeTest`
- Entry point: untracked IntelliJ JUnit run configuration
- Runtime: JetBrains Runtime OpenJDK 17.0.11
- Result: 1 test passed, 0 failed, process exit code 0
- Observed wall time: approximately 2.442 seconds

## What the test proves

The test makes two real provider requests through `DeepSeekAgentModelClient`.
Its executable assertions require all of the following:

1. The first response finishes with `tool_calls`.
2. The model emits exactly one call to the forced `echo_word` function.
3. The emitted function name is `echo_word`.
4. The local tool result `Redis` is appended to the same message history.
5. The second response finishes with `stop` and contains `Redis` in the final
   assistant content.

The test is opt-in: ordinary builds skip it unless both
`DEVMIND_RUN_DEEPSEEK_SMOKE=true` and a non-blank
`DEVMIND_DEEPSEEK_API_KEY` are injected into the test process. The credential
value is not written to this record, source code, logs, or a tracked run
configuration.

## Claim boundary

This is a protocol and integration smoke check only. It does not establish
retrieval accuracy, diagnosis quality, production availability, cost, or an
advantage for any of the preregistered four evaluation arms. This smoke record predates the separate scored run. See the
[four-arm result](phase-d-four-arm-provider-run-v2-2026-08-23.md) for that
experiment and its negative Reviewer-uplift conclusion.
