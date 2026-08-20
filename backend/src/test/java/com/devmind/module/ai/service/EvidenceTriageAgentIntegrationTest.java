package com.devmind.module.ai.service;

import com.devmind.module.ai.agent.AgentBudgetLimits;
import com.devmind.module.ai.agent.AgentExperimentArm;
import com.devmind.module.ai.agent.AgentMessage;
import com.devmind.module.ai.agent.AgentModelClient;
import com.devmind.module.ai.agent.AgentModelRequest;
import com.devmind.module.ai.agent.AgentModelResponse;
import com.devmind.module.ai.agent.AgentReadTool;
import com.devmind.module.ai.agent.AgentRunStatus;
import com.devmind.module.ai.agent.AgentStepType;
import com.devmind.module.ai.agent.AgentTokenUsage;
import com.devmind.module.ai.agent.AgentToolCall;
import com.devmind.module.ai.agent.AgentToolChoice;
import com.devmind.module.ai.agent.AgentToolContext;
import com.devmind.module.ai.agent.AgentToolDefinition;
import com.devmind.module.ai.agent.EvidenceTriageInput;
import com.devmind.module.ai.agent.ScriptedAgentModelClient;
import com.devmind.module.ai.agent.TriageDiagnosis;
import com.devmind.module.ai.agent.TriageRootCause;
import com.devmind.module.ai.entity.AgentRun;
import com.devmind.module.ai.tool.AgentReadToolRegistry;
import com.devmind.module.ai.tool.GetAskLogEvidenceReadTool;
import com.devmind.module.ai.tool.GetChunkEvidenceReadTool;
import com.devmind.module.ai.tool.SearchKnowledgeReadTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.url=jdbc:h2:mem:evidence_triage_agent_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "devmind.ai.provider=mock"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EvidenceTriageAgentIntegrationTest {

    private static final Long USER_ID = 17L;

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AgentRunPersistenceService persistenceService;
    private final EvidenceTriageAgent triageAgent;
    private final AtomicReference<DevelopmentCase> activeCase = new AtomicReference<>();

    @MockBean
    private AgentReadToolRegistry toolRegistry;

    private DevelopmentDataset dataset;

    @Autowired
    EvidenceTriageAgentIntegrationTest(DataSource dataSource,
                                       JdbcTemplate jdbcTemplate,
                                       ObjectMapper objectMapper,
                                       AgentRunPersistenceService persistenceService,
                                       EvidenceTriageAgent triageAgent) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.persistenceService = persistenceService;
        this.triageAgent = triageAgent;
    }

    @BeforeAll
    void createSchemaAndLoadDataset() throws IOException {
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
                USER_ID, "triage-agent-test");
        dataset = objectMapper.readValue(
                Files.readString(evaluationFile("v2-development-bad-cases-v0.1.json")),
                DevelopmentDataset.class
        );
    }

    @BeforeEach
    void configureReadOnlyToolDouble() {
        when(toolRegistry.definitions()).thenReturn(toolDefinitions());
        when(toolRegistry.requireAllowed(anyString())).thenAnswer(invocation ->
                toolFor(invocation.getArgument(0, String.class)));
    }

    @Test
    void shouldRunAllSixPreregisteredRootCausesThroughTheBoundedLoop() throws IOException {
        assertThat(dataset.caseCount()).isEqualTo(6);
        assertThat(dataset.cases()).hasSize(6);
        assertThat(dataset.cases().stream().map(DevelopmentCase::goldRootCause))
                .containsExactlyInAnyOrderElementsOf(
                        EnumSet.allOf(TriageRootCause.class).stream().map(Enum::name).toList());
        JsonNode protocol = objectMapper.readTree(Files.readString(
                evaluationFile("v2-preregistered-protocol.json")));
        Set<String> protocolLabels = objectMapper.convertValue(
                protocol.path("rootCauseLabels"),
                objectMapper.getTypeFactory().constructCollectionType(Set.class, String.class));
        assertThat(dataset.cases().stream().map(DevelopmentCase::goldRootCause).toList())
                .containsExactlyInAnyOrderElementsOf(protocolLabels);

        for (DevelopmentCase developmentCase : dataset.cases()) {
            activeCase.set(developmentCase);
            AgentRun run = startRun("phase-b-" + developmentCase.caseId());
            CapturingClient modelClient = new CapturingClient(scriptFor(developmentCase));

            TriageDiagnosis diagnosis = triageAgent.triage(
                    new AgentToolContext(USER_ID, run.getId()),
                    modelClient,
                    new EvidenceTriageInput(
                            developmentCase.askLogId(),
                            developmentCase.issueDescription(),
                            developmentCase.expectedAnswer())
            );

            TriageRootCause expected = TriageRootCause.valueOf(developmentCase.goldRootCause());
            assertThat(diagnosis.rootCause()).isEqualTo(expected);
            assertThat(diagnosis.recommendedRoute()).isEqualTo(expected.requiredRoute());
            AgentRun stored = persistenceService.getOwnedRun(USER_ID, run.getId());
            assertThat(stored.getStatus()).isEqualTo(AgentRunStatus.SUCCEEDED.name());
            assertThat(stored.getUsedSteps()).isEqualTo(5);
            assertThat(stored.getUsedModelCalls()).isEqualTo(3);
            assertThat(persistenceService.replaySteps(USER_ID, run.getId()))
                    .extracting(step -> AgentStepType.valueOf(step.getStepType()))
                    .containsExactly(
                            AgentStepType.MODEL_CALL,
                            AgentStepType.TOOL_CALL,
                            AgentStepType.MODEL_CALL,
                            AgentStepType.TOOL_CALL,
                            AgentStepType.MODEL_CALL);
            assertReconstructedConversation(modelClient.requests(), developmentCase);
        }
    }

    @Test
    void shouldRejectCorrectEvidenceDiagnosisForLegacyPromptSchema() {
        DevelopmentCase source = dataset.cases().stream()
                .filter(item -> item.goldRootCause()
                        .equals(TriageRootCause.ANSWER_WRONG_WITH_CORRECT_EVIDENCE.name()))
                .findFirst().orElseThrow();
        DevelopmentCase legacy = source.withEligibility(false);
        activeCase.set(legacy);
        AgentRun run = startRun("phase-b-legacy-grounding");

        assertThatThrownBy(() -> triageAgent.triage(
                new AgentToolContext(USER_ID, run.getId()),
                new CapturingClient(scriptFor(legacy)),
                inputFor(legacy)))
                .hasMessageContaining("legacy or unknown prompt schema");
        assertFailedRun(run);
    }

    @Test
    void shouldRejectAnAskLogResultForAnotherTarget() {
        DevelopmentCase developmentCase = dataset.cases().get(0);
        activeCase.set(developmentCase);
        AgentRun run = startRun("phase-b-wrong-ask-log");
        List<AgentModelResponse> script = List.of(
                toolCallResponse(
                        "ask-wrong-target",
                        GetAskLogEvidenceReadTool.NAME,
                        "{\"askLogId\":" + (developmentCase.askLogId() + 1) + "}")
        );

        assertThatThrownBy(() -> triageAgent.triage(
                new AgentToolContext(USER_ID, run.getId()),
                new CapturingClient(script),
                inputFor(developmentCase)))
                .hasMessageContaining("only its target ask log");
        assertFailedRun(run);
    }

    @Test
    void shouldRejectHallucinatedEvidenceReferenceAndFailTheRun() {
        DevelopmentCase developmentCase = dataset.cases().get(0);
        activeCase.set(developmentCase);
        AgentRun run = startRun("phase-b-hallucinated-evidence");
        List<AgentModelResponse> script = List.of(
                toolCallResponse("ask-real", GetAskLogEvidenceReadTool.NAME,
                        "{\"askLogId\":" + developmentCase.askLogId() + "}"),
                finalResponse(diagnosisJson(
                        developmentCase,
                        "ask-never-executed",
                        "investigation-never-executed"))
        );

        assertThatThrownBy(() -> triageAgent.triage(
                new AgentToolContext(USER_ID, run.getId()),
                new CapturingClient(script),
                inputFor(developmentCase)))
                .hasMessageContaining("unexecuted tool call");
        assertFailedRun(run);
    }

    @Test
    void shouldRejectDuplicateToolCallIdsBeforeExecutingTheBatch() {
        DevelopmentCase developmentCase = dataset.cases().get(0);
        activeCase.set(developmentCase);
        AgentRun run = startRun("phase-b-duplicate-tool-call");
        AgentModelResponse duplicateBatch = new AgentModelResponse(
                AgentMessage.assistantToolCalls(null, List.of(
                        AgentToolCall.function("duplicate-id", SearchKnowledgeReadTool.NAME,
                                "{\"query\":\"one\"}"),
                        AgentToolCall.function("duplicate-id", SearchKnowledgeReadTool.NAME,
                                "{\"query\":\"two\"}")
                )),
                "tool_calls",
                "scripted:test",
                new AgentTokenUsage(5, 2, 7)
        );
        List<AgentModelResponse> script = List.of(
                toolCallResponse("ask-first", GetAskLogEvidenceReadTool.NAME,
                        "{\"askLogId\":" + developmentCase.askLogId() + "}"),
                duplicateBatch
        );

        assertThatThrownBy(() -> triageAgent.triage(
                new AgentToolContext(USER_ID, run.getId()),
                new CapturingClient(script),
                inputFor(developmentCase)))
                .hasMessageContaining("duplicate tool call id");
        assertFailedRun(run);
        assertThat(persistenceService.replaySteps(USER_ID, run.getId()))
                .extracting(step -> AgentStepType.valueOf(step.getStepType()))
                .containsExactly(
                        AgentStepType.MODEL_CALL,
                        AgentStepType.TOOL_CALL,
                        AgentStepType.MODEL_CALL);
    }

    @Test
    void shouldEnforceThePreregisteredTwelveToolCallLimit() {
        DevelopmentCase developmentCase = dataset.cases().get(0);
        activeCase.set(developmentCase);
        AgentRun run = startRun("phase-b-tool-call-limit");
        List<AgentToolCall> oversizedBatch = IntStream.range(0, 12)
                .mapToObj(index -> AgentToolCall.function(
                        "search-" + index,
                        SearchKnowledgeReadTool.NAME,
                        "{\"query\":\"bounded-" + index + "\"}"))
                .toList();
        AgentModelResponse oversizedResponse = new AgentModelResponse(
                AgentMessage.assistantToolCalls(null, oversizedBatch),
                "tool_calls",
                "scripted:test",
                new AgentTokenUsage(8, 3, 11)
        );
        List<AgentModelResponse> script = List.of(
                toolCallResponse("ask-budget", GetAskLogEvidenceReadTool.NAME,
                        "{\"askLogId\":" + developmentCase.askLogId() + "}"),
                oversizedResponse
        );

        assertThatThrownBy(() -> triageAgent.triage(
                new AgentToolContext(USER_ID, run.getId()),
                new CapturingClient(script),
                inputFor(developmentCase)))
                .hasMessageContaining("tool-call budget exhausted");
        assertFailedRun(run);
        assertThat(persistenceService.replaySteps(USER_ID, run.getId())).hasSize(3);
    }

    private AgentRun startRun(String idempotencyKey) {
        return persistenceService.startRun(
                USER_ID,
                null,
                AgentExperimentArm.SINGLE,
                new AgentBudgetLimits(6, 3, 3, 1_000, Duration.ofSeconds(30)),
                idempotencyKey
        );
    }

    private EvidenceTriageInput inputFor(DevelopmentCase developmentCase) {
        return new EvidenceTriageInput(
                developmentCase.askLogId(),
                developmentCase.issueDescription(),
                developmentCase.expectedAnswer());
    }

    private List<AgentModelResponse> scriptFor(DevelopmentCase developmentCase) {
        String askCallId = developmentCase.caseId() + "-ask";
        String investigateCallId = developmentCase.caseId() + "-investigate";
        return List.of(
                toolCallResponse(
                        askCallId,
                        GetAskLogEvidenceReadTool.NAME,
                        "{\"askLogId\":" + developmentCase.askLogId() + "}"),
                toolCallResponse(
                        investigateCallId,
                        developmentCase.secondTool(),
                        secondToolArguments(developmentCase)),
                finalResponse(diagnosisJson(
                        developmentCase, askCallId, investigateCallId))
        );
    }

    private String secondToolArguments(DevelopmentCase developmentCase) {
        if (GetChunkEvidenceReadTool.NAME.equals(developmentCase.secondTool())) {
            ObjectNode arguments = objectMapper.createObjectNode();
            ArrayNode ids = arguments.putArray("chunkIds");
            developmentCase.returnedChunkIds().forEach(ids::add);
            return arguments.toString();
        }
        return objectMapper.createObjectNode()
                .put("query", developmentCase.caseId())
                .put("limit", 5)
                .toString();
    }

    private AgentModelResponse toolCallResponse(String callId, String name, String arguments) {
        return new AgentModelResponse(
                AgentMessage.assistantToolCalls(
                        null, List.of(AgentToolCall.function(callId, name, arguments))),
                "tool_calls",
                "scripted:test",
                new AgentTokenUsage(5, 2, 7)
        );
    }

    private AgentModelResponse finalResponse(String content) {
        return new AgentModelResponse(
                AgentMessage.assistant(content),
                "stop",
                "scripted:test",
                new AgentTokenUsage(8, 4, 12)
        );
    }

    private String diagnosisJson(DevelopmentCase developmentCase,
                                 String askCallId,
                                 String investigateCallId) {
        TriageRootCause rootCause = TriageRootCause.valueOf(
                developmentCase.goldRootCause());
        ObjectNode diagnosis = objectMapper.createObjectNode();
        diagnosis.put("rootCause", rootCause.wireValue());
        diagnosis.put("summary", developmentCase.diagnosisSummary());
        ArrayNode evidence = diagnosis.putArray("evidence");
        evidence.addObject()
                .put("toolCallId", askCallId)
                .put("askLogId", developmentCase.askLogId())
                .put("observation", "The target ask log was inspected.");
        ObjectNode investigation = evidence.addObject()
                .put("toolCallId", investigateCallId)
                .put("observation", developmentCase.returnedChunkIds().isEmpty()
                        ? "The targeted current-knowledge search returned no chunks."
                        : "The investigation returned supporting current or archived chunks.");
        if (!developmentCase.returnedChunkIds().isEmpty()) {
            investigation.put("chunkId", developmentCase.returnedChunkIds().get(0));
        }
        diagnosis.put("recommendedRoute", rootCause.requiredRoute().wireValue());
        diagnosis.put("confidence", developmentCase.confidence());
        return diagnosis.toString();
    }

    private void assertReconstructedConversation(List<AgentModelRequest> requests,
                                                 DevelopmentCase developmentCase) {
        assertThat(requests).hasSize(3);
        assertThat(requests.get(0).toolChoice().mode()).isEqualTo(AgentToolChoice.Mode.FUNCTION);
        assertThat(requests.get(0).toolChoice().functionName())
                .isEqualTo(GetAskLogEvidenceReadTool.NAME);
        assertThat(requests.get(0).messages().get(0).content())
                .contains("untrusted evidence", "return only one JSON object");
        assertThat(requests.get(0).messages().get(1).content())
                .contains(developmentCase.issueDescription(),
                        developmentCase.askLogId().toString(),
                        "\"expectedAnswerIsEvidence\":false");
        assertThat(requests.get(1).messages())
                .extracting(AgentMessage::role)
                .containsExactly(
                        AgentMessage.Role.SYSTEM,
                        AgentMessage.Role.USER,
                        AgentMessage.Role.ASSISTANT,
                        AgentMessage.Role.TOOL);
        assertThat(requests.get(2).messages())
                .extracting(AgentMessage::role)
                .containsExactly(
                        AgentMessage.Role.SYSTEM,
                        AgentMessage.Role.USER,
                        AgentMessage.Role.ASSISTANT,
                        AgentMessage.Role.TOOL,
                        AgentMessage.Role.ASSISTANT,
                        AgentMessage.Role.TOOL);
    }

    private void assertFailedRun(AgentRun run) {
        AgentRun stored = persistenceService.getOwnedRun(USER_ID, run.getId());
        assertThat(stored.getStatus()).isEqualTo(AgentRunStatus.FAILED.name());
        assertThat(stored.getErrorCode()).isEqualTo("TRIAGE_ORCHESTRATION_FAILED");
        assertThat(stored.getErrorMessage()).isEqualTo("evidence triage orchestration failed");
    }

    private List<AgentToolDefinition> toolDefinitions() {
        return List.of(
                definition(SearchKnowledgeReadTool.NAME),
                definition(GetChunkEvidenceReadTool.NAME),
                definition(GetAskLogEvidenceReadTool.NAME)
        );
    }

    private AgentToolDefinition definition(String name) {
        return new AgentToolDefinition(
                name,
                "Test fixture for " + name,
                objectMapper.createObjectNode().put("type", "object")
        );
    }

    private AgentReadTool toolFor(String name) {
        AgentToolDefinition definition = definition(name);
        return new AgentReadTool() {
            @Override
            public AgentToolDefinition definition() {
                return definition;
            }

            @Override
            public JsonNode execute(AgentToolContext context, JsonNode arguments) {
                DevelopmentCase developmentCase = activeCase.get();
                if (GetAskLogEvidenceReadTool.NAME.equals(name)) {
                    return objectMapper.createObjectNode()
                            .put("askLogId", arguments.path("askLogId").asLong())
                            .put("answerGroundingEvaluationEligible",
                                    developmentCase.answerGroundingEvaluationEligible());
                }
                ObjectNode result = objectMapper.createObjectNode();
                ArrayNode items = result.putArray("items");
                for (Long chunkId : developmentCase.returnedChunkIds()) {
                    items.addObject()
                            .put("chunkId", chunkId)
                            .put("content", "scripted evidence for " + developmentCase.caseId());
                }
                result.put("count", items.size());
                return result;
            }
        };
    }

    private Path evaluationFile(String name) {
        Path direct = Path.of("evaluation", name).toAbsolutePath();
        if (Files.isRegularFile(direct)) {
            return direct;
        }
        Path fromRepositoryRoot = Path.of("backend", "evaluation", name).toAbsolutePath();
        if (Files.isRegularFile(fromRepositoryRoot)) {
            return fromRepositoryRoot;
        }
        throw new IllegalStateException("evaluation file not found: " + name);
    }

    private static final class CapturingClient implements AgentModelClient {

        private final ScriptedAgentModelClient delegate;
        private final List<AgentModelRequest> requests = new ArrayList<>();

        private CapturingClient(List<AgentModelResponse> script) {
            this.delegate = new ScriptedAgentModelClient(script);
        }

        @Override
        public boolean supports(String provider) {
            return delegate.supports(provider);
        }

        @Override
        public AgentModelResponse complete(AgentModelRequest request) {
            requests.add(request);
            return delegate.complete(request);
        }

        private List<AgentModelRequest> requests() {
            return List.copyOf(requests);
        }
    }

    private record DevelopmentDataset(
            int schemaVersion,
            String datasetId,
            String datasetVersion,
            String status,
            String scope,
            int caseCount,
            String allocation,
            List<DevelopmentCase> cases
    ) {
    }

    private record DevelopmentCase(
            String caseId,
            Long askLogId,
            String issueDescription,
            String expectedAnswer,
            String goldRootCause,
            String secondTool,
            List<Long> returnedChunkIds,
            boolean answerGroundingEvaluationEligible,
            String diagnosisSummary,
            double confidence
    ) {
        private DevelopmentCase withEligibility(boolean eligible) {
            return new DevelopmentCase(
                    caseId, askLogId, issueDescription, expectedAnswer, goldRootCause,
                    secondTool, returnedChunkIds, eligible, diagnosisSummary, confidence);
        }
    }
}
