package com.devmind.module.ai.service;

import com.devmind.module.ai.agent.AgentBudgetLimits;
import com.devmind.module.ai.agent.AgentExperimentArm;
import com.devmind.module.ai.agent.AgentMessage;
import com.devmind.module.ai.agent.AgentReadTool;
import com.devmind.module.ai.agent.AgentRole;
import com.devmind.module.ai.agent.AgentRunStatus;
import com.devmind.module.ai.agent.AgentStepStatus;
import com.devmind.module.ai.agent.AgentToolCall;
import com.devmind.module.ai.agent.AgentToolContext;
import com.devmind.module.ai.agent.AgentToolDefinition;
import com.devmind.module.ai.entity.AgentRun;
import com.devmind.module.ai.tool.AgentReadToolRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.url=jdbc:h2:mem:agent_tool_executor_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "devmind.ai.provider=mock"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AgentToolCallExecutorIntegrationTest {

    private static final Long USER_ID = 9L;

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final AgentRunPersistenceService persistenceService;
    private final AgentToolCallExecutor executor;
    private final TransactionOperations transactionOperations;
    private final ProbeState probeState = new ProbeState();

    @MockBean
    private AgentReadToolRegistry toolRegistry;

    @Autowired
    AgentToolCallExecutorIntegrationTest(DataSource dataSource,
                                         JdbcTemplate jdbcTemplate,
                                         AgentRunPersistenceService persistenceService,
                                         AgentToolCallExecutor executor,
                                         TransactionOperations transactionOperations) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
        this.persistenceService = persistenceService;
        this.executor = executor;
        this.transactionOperations = transactionOperations;
    }

    @BeforeAll
    void createSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE user_account (
                    id BIGINT PRIMARY KEY,
                    username VARCHAR(64) NOT NULL UNIQUE
                )
                """);
        new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/V6__create_agent_run_and_step_tables.sql"),
                new ClassPathResource("db/migration/V7__add_agent_step_tool_call_id.sql"),
                new ClassPathResource("db/migration/V8__add_agent_tool_call_budget.sql")
        ).execute(dataSource);
        jdbcTemplate.update("INSERT INTO user_account (id, username) VALUES (?, ?)",
                USER_ID, "tool-test");
    }

    @BeforeEach
    void registerProbeTool() {
        when(toolRegistry.requireAllowed(anyString())).thenReturn(probeTool());
    }

    @Test
    void shouldSuspendCallerTransactionInjectContextAndPersistBoundedAudit() {
        AgentRun run = startRun("tool-executor-success");
        AtomicReference<AgentMessage> response = new AtomicReference<>();

        transactionOperations.executeWithoutResult(status -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            response.set(executor.execute(
                    new AgentToolContext(USER_ID, run.getId()),
                    AgentRole.EVIDENCE_TRIAGE,
                    AgentToolCall.function("call-probe-1", "transactionProbe",
                            "{\"query\":\"private raw value\"}")));
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
        });

        assertThat(probeState.transactionActive.get()).isFalse();
        assertThat(probeState.context.get().userId()).isEqualTo(USER_ID);
        assertThat(response.get().role()).isEqualTo(AgentMessage.Role.TOOL);
        assertThat(response.get().content()).contains("visible only to model");
        AgentRun stored = persistenceService.getOwnedRun(USER_ID, run.getId());
        assertThat(stored.getUsedSteps()).isEqualTo(1);
        assertThat(stored.getUsedModelCalls()).isZero();
        assertThat(persistenceService.replaySteps(USER_ID, run.getId()))
                .singleElement()
                .satisfies(step -> {
                    assertThat(step.getToolCallId()).isEqualTo("call-probe-1");
                    assertThat(step.getToolName()).isEqualTo("transactionProbe");
                    assertThat(step.getInputSummary())
                            .contains("argumentFields=query")
                            .doesNotContain("private raw value");
                    assertThat(step.getOutputSummary())
                            .contains("resultChars=")
                            .doesNotContain("visible only to model");
                });
    }

    @Test
    void shouldFailRunAndHideUnexpectedToolExceptionMessage() {
        AgentRun run = startRun("tool-executor-failure");

        assertThatThrownBy(() -> executor.execute(
                new AgentToolContext(USER_ID, run.getId()),
                AgentRole.EVIDENCE_TRIAGE,
                AgentToolCall.function("call-probe-fail", "transactionProbe", "{\"fail\":true}")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("secret provider-style detail");

        assertThat(persistenceService.getOwnedRun(USER_ID, run.getId()).getStatus())
                .isEqualTo(AgentRunStatus.FAILED.name());
        assertThat(persistenceService.replaySteps(USER_ID, run.getId()))
                .singleElement()
                .satisfies(step -> {
                    assertThat(step.getStatus()).isEqualTo(AgentStepStatus.FAILED.name());
                    assertThat(step.getErrorMessage())
                            .isEqualTo("agent tool execution failed")
                            .doesNotContain("secret");
                });
    }

    private AgentRun startRun(String key) {
        return persistenceService.startRun(
                USER_ID,
                null,
                AgentExperimentArm.SINGLE,
                new AgentBudgetLimits(3, 1, 2, 100, Duration.ofSeconds(30)),
                key
        );
    }

    private AgentReadTool probeTool() {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode schema = objectMapper.createObjectNode()
                .put("type", "object")
                .put("additionalProperties", true);
        AgentToolDefinition definition = new AgentToolDefinition(
                "transactionProbe", "Test-only transaction probe.", schema);
        return new AgentReadTool() {
            @Override
            public AgentToolDefinition definition() {
                return definition;
            }

            @Override
            public JsonNode execute(AgentToolContext context, JsonNode arguments) {
                probeState.context.set(context);
                probeState.transactionActive.set(
                        TransactionSynchronizationManager.isActualTransactionActive());
                if (arguments.path("fail").asBoolean(false)) {
                    throw new IllegalStateException("secret provider-style detail");
                }
                return objectMapper.createObjectNode().put("result", "visible only to model");
            }
        };
    }

    static class ProbeState {
        private final AtomicReference<AgentToolContext> context = new AtomicReference<>();
        private final AtomicReference<Boolean> transactionActive = new AtomicReference<>();
    }
}
