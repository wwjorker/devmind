package com.devmind.module.ai.service;

import com.devmind.common.exception.BizException;
import com.devmind.module.ai.agent.AgentBudgetLimits;
import com.devmind.module.ai.agent.AgentExperimentArm;
import com.devmind.module.ai.agent.AgentMessage;
import com.devmind.module.ai.agent.AgentModelClient;
import com.devmind.module.ai.agent.AgentModelRequest;
import com.devmind.module.ai.agent.AgentModelResponse;
import com.devmind.module.ai.agent.AgentRole;
import com.devmind.module.ai.agent.AgentRunStatus;
import com.devmind.module.ai.agent.AgentStepStatus;
import com.devmind.module.ai.agent.AgentTokenUsage;
import com.devmind.module.ai.agent.AgentToolCall;
import com.devmind.module.ai.agent.ScriptedAgentModelClient;
import com.devmind.module.ai.entity.AgentRun;
import com.devmind.module.ai.entity.AgentStep;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.url=jdbc:h2:mem:agent_run_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "devmind.ai.provider=mock"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AgentRunPersistenceIntegrationTest {

    private static final Long USER_ID = 7L;

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final AgentRunPersistenceService persistenceService;
    private final AgentModelStepExecutor stepExecutor;
    private final TransactionOperations transactionOperations;

    @Autowired
    AgentRunPersistenceIntegrationTest(DataSource dataSource,
                                       JdbcTemplate jdbcTemplate,
                                       AgentRunPersistenceService persistenceService,
                                       AgentModelStepExecutor stepExecutor,
                                       TransactionOperations transactionOperations) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
        this.persistenceService = persistenceService;
        this.stepExecutor = stepExecutor;
        this.transactionOperations = transactionOperations;
    }

    @BeforeAll
    void createSchemaFromProductionMigration() {
        jdbcTemplate.execute("""
                CREATE TABLE user_account (
                    id BIGINT PRIMARY KEY,
                    username VARCHAR(64) NOT NULL UNIQUE
                )
                """);
        new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/V6__create_agent_run_and_step_tables.sql"),
                new ClassPathResource("db/migration/V7__add_agent_step_tool_call_id.sql")
        ).execute(dataSource);
        jdbcTemplate.update("INSERT INTO user_account (id, username) VALUES (?, ?)", USER_ID, "agent-test");
    }

    @Test
    void shouldPersistReplayAndSuspendAnExistingTransactionAroundModelCall() {
        AgentRun run = startRun("run-transaction-boundary", new AgentBudgetLimits(
                3, 2, 100, Duration.ofSeconds(30)));
        AgentModelResponse expected = textResponse("completed", 10, 5, 15);
        TransactionProbeModelClient modelClient = new TransactionProbeModelClient(expected);
        AgentModelRequest request = new AgentModelRequest(
                List.of(AgentMessage.user("sensitive question must not be stored")),
                List.of(),
                null
        );

        AtomicReference<AgentModelResponse> actual = new AtomicReference<>();
        transactionOperations.executeWithoutResult(status -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            actual.set(stepExecutor.execute(
                    USER_ID, run.getId(), AgentRole.EVIDENCE_TRIAGE, modelClient, request));
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
        });
        AgentRunStatus completion = persistenceService.markSucceeded(
                USER_ID, run.getId(), "triage finished");

        assertThat(actual.get()).isSameAs(expected);
        assertThat(modelClient.transactionActiveDuringCall()).isFalse();
        assertThat(completion).isEqualTo(AgentRunStatus.SUCCEEDED);

        AgentRun stored = persistenceService.getOwnedRun(USER_ID, run.getId());
        assertThat(stored.getStatus()).isEqualTo(AgentRunStatus.SUCCEEDED.name());
        assertThat(stored.getUsedSteps()).isEqualTo(1);
        assertThat(stored.getUsedModelCalls()).isEqualTo(1);
        assertThat(stored.getUsedPromptTokens()).isEqualTo(10);
        assertThat(stored.getUsedCompletionTokens()).isEqualTo(5);
        assertThat(stored.getUsedTotalTokens()).isEqualTo(15);
        assertThat(stored.getResultSummary()).isEqualTo("triage finished");

        List<AgentStep> replay = persistenceService.replaySteps(USER_ID, run.getId());
        assertThat(replay).singleElement().satisfies(step -> {
            assertThat(step.getSequenceNo()).isEqualTo(1);
            assertThat(step.getRoleName()).isEqualTo(AgentRole.EVIDENCE_TRIAGE.name());
            assertThat(step.getInputSummary())
                    .contains("messages=1")
                    .doesNotContain("sensitive question");
            assertThat(step.getOutputSummary())
                    .contains("finishReason=stop", "contentChars=9")
                    .doesNotContain("completed");
            assertThat(step.getTotalTokens()).isEqualTo(15);
        });
    }

    @Test
    void shouldHardStopBeforeASecondCallWhenStepBudgetIsExhausted() {
        AgentRun run = startRun("run-step-budget", new AgentBudgetLimits(
                1, 2, 100, Duration.ofSeconds(30)));
        ScriptedAgentModelClient modelClient = new ScriptedAgentModelClient(List.of(
                toolCallResponse(5)
        ));
        AgentModelRequest request = request();

        stepExecutor.execute(USER_ID, run.getId(), AgentRole.EVIDENCE_TRIAGE, modelClient, request);

        assertThatThrownBy(() -> stepExecutor.execute(
                USER_ID, run.getId(), AgentRole.EVIDENCE_TRIAGE, modelClient, request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("max_steps");
        assertThat(modelClient.consumedResponses()).isEqualTo(1);
        assertThat(persistenceService.getOwnedRun(USER_ID, run.getId()).getStatus())
                .isEqualTo(AgentRunStatus.BUDGET_EXHAUSTED.name());
        assertThat(persistenceService.replaySteps(USER_ID, run.getId())).hasSize(1);
    }

    @Test
    void shouldAuditToolStepWithoutConsumingModelCallBudget() {
        AgentRun run = startRun("run-tool-budget", new AgentBudgetLimits(
                3, 1, 100, Duration.ofSeconds(30)));
        ScriptedAgentModelClient modelClient = new ScriptedAgentModelClient(List.of(
                toolCallResponse(5)
        ));
        stepExecutor.execute(USER_ID, run.getId(), AgentRole.EVIDENCE_TRIAGE,
                modelClient, request());

        var reservation = persistenceService.reserveToolStep(
                USER_ID,
                run.getId(),
                AgentRole.EVIDENCE_TRIAGE,
                "searchKnowledge",
                "call-after-model-budget",
                "tool=searchKnowledge;argumentFields=query"
        );
        assertThat(reservation.permitted()).isTrue();
        assertThat(persistenceService.completeToolStep(
                USER_ID, run.getId(), reservation.stepId(), 2, "resultChars=42"))
                .isEqualTo(AgentRunStatus.RUNNING);

        AgentRun stored = persistenceService.getOwnedRun(USER_ID, run.getId());
        assertThat(stored.getUsedSteps()).isEqualTo(2);
        assertThat(stored.getUsedModelCalls()).isEqualTo(1);
        assertThat(persistenceService.replaySteps(USER_ID, run.getId()))
                .hasSize(2)
                .element(1)
                .satisfies(step -> {
                    assertThat(step.getStepType()).isEqualTo("TOOL_CALL");
                    assertThat(step.getToolName()).isEqualTo("searchKnowledge");
                    assertThat(step.getToolCallId()).isEqualTo("call-after-model-budget");
                    assertThat(step.getTotalTokens()).isZero();
                });
    }

    @Test
    void shouldRecordLastUsageAndBlockTheNextCallAfterTokenThresholdIsCrossed() {
        AgentRun run = startRun("run-token-budget", new AgentBudgetLimits(
                3, 3, 10, Duration.ofSeconds(30)));
        ScriptedAgentModelClient modelClient = new ScriptedAgentModelClient(List.of(
                toolCallResponse(12)
        ));
        AgentModelRequest request = request();

        stepExecutor.execute(USER_ID, run.getId(), AgentRole.EVIDENCE_TRIAGE, modelClient, request);

        AgentRun afterFirstCall = persistenceService.getOwnedRun(USER_ID, run.getId());
        assertThat(afterFirstCall.getUsedTotalTokens()).isEqualTo(12);
        assertThatThrownBy(() -> stepExecutor.execute(
                USER_ID, run.getId(), AgentRole.EVIDENCE_TRIAGE, modelClient, request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("max_total_tokens");
        assertThat(modelClient.consumedResponses()).isEqualTo(1);
    }

    @Test
    void shouldReuseAnEquivalentIdempotencyKeyAndRejectDifferentBudgets() {
        AgentBudgetLimits limits = new AgentBudgetLimits(3, 2, 100, Duration.ofSeconds(30));
        AgentRun first = startRun("run-idempotent", limits);
        AgentRun repeated = startRun("run-idempotent", limits);

        assertThat(repeated.getId()).isEqualTo(first.getId());
        assertThatThrownBy(() -> startRun(
                "run-idempotent",
                new AgentBudgetLimits(4, 2, 100, Duration.ofSeconds(30))))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("different agent run request");
    }

    @Test
    void shouldPersistAProviderFailureWithoutStoringItsUntrustedMessage() {
        AgentRun run = startRun("run-provider-failure", new AgentBudgetLimits(
                3, 2, 100, Duration.ofSeconds(30)));
        AgentModelClient failingClient = new AgentModelClient() {
            @Override
            public boolean supports(String provider) {
                return true;
            }

            @Override
            public AgentModelResponse complete(AgentModelRequest request) {
                throw new IllegalStateException("untrusted provider body with sensitive content");
            }
        };

        assertThatThrownBy(() -> stepExecutor.execute(
                USER_ID, run.getId(), AgentRole.EVIDENCE_TRIAGE, failingClient, request()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("untrusted provider body");

        AgentRun stored = persistenceService.getOwnedRun(USER_ID, run.getId());
        assertThat(stored.getStatus()).isEqualTo(AgentRunStatus.FAILED.name());
        assertThat(stored.getErrorCode()).isEqualTo("IllegalStateException");
        assertThat(stored.getErrorMessage())
                .isEqualTo("external model call failed")
                .doesNotContain("sensitive content");
        assertThat(persistenceService.replaySteps(USER_ID, run.getId()))
                .singleElement()
                .satisfies(step -> {
                    assertThat(step.getStatus()).isEqualTo(AgentStepStatus.FAILED.name());
                    assertThat(step.getErrorMessage()).isEqualTo("external model call failed");
                });
    }

    @Test
    void shouldNotOversubscribeBudgetWhenModelCallsOverlap() throws Exception {
        AgentRun run = startRun("run-concurrent-budget", new AgentBudgetLimits(
                1, 1, 100, Duration.ofSeconds(30)));
        CountDownLatch firstCallEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstCall = new CountDownLatch(1);
        AgentModelClient blockingClient = new AgentModelClient() {
            @Override
            public boolean supports(String provider) {
                return true;
            }

            @Override
            public AgentModelResponse complete(AgentModelRequest request) {
                firstCallEntered.countDown();
                try {
                    if (!releaseFirstCall.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("test model release timed out");
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("test model interrupted", ex);
                }
                return textResponse("done", 2, 1, 3);
            }
        };
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            Future<AgentModelResponse> first = executorService.submit(() -> stepExecutor.execute(
                    USER_ID, run.getId(), AgentRole.EVIDENCE_TRIAGE, blockingClient, request()));
            assertThat(firstCallEntered.await(5, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> stepExecutor.execute(
                    USER_ID, run.getId(), AgentRole.EVIDENCE_TRIAGE, blockingClient, request()))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("max_steps");

            releaseFirstCall.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS).assistantMessage().content()).isEqualTo("done");
            assertThat(persistenceService.replaySteps(USER_ID, run.getId())).hasSize(1);
        } finally {
            releaseFirstCall.countDown();
            executorService.shutdownNow();
        }
    }

    @Test
    void shouldCancelIdempotentlyAndBlockFutureModelCalls() {
        AgentRun run = startRun("run-cancelled", new AgentBudgetLimits(
                3, 2, 100, Duration.ofSeconds(30)));
        ScriptedAgentModelClient modelClient = new ScriptedAgentModelClient(List.of(
                textResponse("must not run", 1, 1, 2)
        ));

        assertThat(persistenceService.cancelRun(USER_ID, run.getId()))
                .isEqualTo(AgentRunStatus.CANCELLED);
        assertThat(persistenceService.cancelRun(USER_ID, run.getId()))
                .isEqualTo(AgentRunStatus.CANCELLED);
        assertThatThrownBy(() -> stepExecutor.execute(
                USER_ID, run.getId(), AgentRole.EVIDENCE_TRIAGE, modelClient, request()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("not active");
        assertThat(modelClient.consumedResponses()).isZero();
        assertThat(persistenceService.replaySteps(USER_ID, run.getId())).isEmpty();
    }

    private AgentRun startRun(String idempotencyKey, AgentBudgetLimits limits) {
        return persistenceService.startRun(
                USER_ID,
                null,
                AgentExperimentArm.SINGLE,
                limits,
                idempotencyKey
        );
    }

    private AgentModelRequest request() {
        return new AgentModelRequest(
                List.of(AgentMessage.user("continue")),
                List.of(),
                null
        );
    }

    private AgentModelResponse textResponse(String content,
                                            int promptTokens,
                                            int completionTokens,
                                            int totalTokens) {
        return new AgentModelResponse(
                AgentMessage.assistant(content),
                "stop",
                "scripted:test",
                new AgentTokenUsage(promptTokens, completionTokens, totalTokens)
        );
    }

    private AgentModelResponse toolCallResponse(int totalTokens) {
        return new AgentModelResponse(
                AgentMessage.assistantToolCalls(
                        null,
                        List.of(AgentToolCall.function("call-1", "search", "{}"))
                ),
                "tool_calls",
                "scripted:test",
                new AgentTokenUsage(totalTokens - 2, 2, totalTokens)
        );
    }

    private static final class TransactionProbeModelClient implements AgentModelClient {

        private final AgentModelResponse response;
        private final AtomicReference<Boolean> transactionActive = new AtomicReference<>();
        private final AtomicInteger calls = new AtomicInteger();

        private TransactionProbeModelClient(AgentModelResponse response) {
            this.response = response;
        }

        @Override
        public boolean supports(String provider) {
            return "transaction-probe".equalsIgnoreCase(provider);
        }

        @Override
        public AgentModelResponse complete(AgentModelRequest request) {
            calls.incrementAndGet();
            transactionActive.set(TransactionSynchronizationManager.isActualTransactionActive());
            return response;
        }

        Boolean transactionActiveDuringCall() {
            return transactionActive.get();
        }
    }
}
