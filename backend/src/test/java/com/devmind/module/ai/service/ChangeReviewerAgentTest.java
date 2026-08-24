package com.devmind.module.ai.service;

import com.devmind.common.exception.BizException;
import com.devmind.module.ai.agent.AgentMessage;
import com.devmind.module.ai.agent.AgentModelClient;
import com.devmind.module.ai.agent.AgentModelResponse;
import com.devmind.module.ai.agent.AgentRunStatus;
import com.devmind.module.ai.agent.AgentTokenUsage;
import com.devmind.module.ai.agent.AgentToolCall;
import com.devmind.module.ai.agent.AgentToolContext;
import com.devmind.module.ai.agent.AgentToolDefinition;
import com.devmind.module.ai.agent.ChangeReviewInput;
import com.devmind.module.ai.agent.ReviewerDecision;
import com.devmind.module.ai.agent.ReviewerDecisionCodec;
import com.devmind.module.ai.agent.ReviewerVerdict;
import com.devmind.module.ai.tool.AgentReadToolRegistry;
import com.devmind.module.ai.tool.GetAskLogEvidenceReadTool;
import com.devmind.module.ai.tool.GetChunkEvidenceReadTool;
import com.devmind.module.ai.tool.SearchKnowledgeReadTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChangeReviewerAgentTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldSearchIndependentlyAndReturnGroundedDecision() {
        Fixture fixture = fixture(
                toolCallResponse("search-1", SearchKnowledgeReadTool.NAME,
                        "{\"query\":\"transaction propagation\"}"),
                finalResponse("{\"verdict\":\"REJECT\","
                        + "\"summary\":\"Contradictory current evidence was found.\","
                        + "\"findings\":[{\"code\":\"OMITTED_COUNTEREVIDENCE\","
                        + "\"severity\":\"BLOCKING\","
                        + "\"description\":\"The proposal omits a conflicting source.\","
                        + "\"evidencePath\":\"counterevidence\","
                        + "\"toolCallId\":\"search-1\",\"chunkId\":55}],"
                        + "\"confidence\":0.92}"));
        when(fixture.toolExecutor.execute(any(), any(), any()))
                .thenReturn(AgentMessage.toolResult(
                        "search-1", "{\"items\":[{\"chunkId\":55}]}"));

        ReviewerDecision decision = fixture.agent.review(
                new AgentToolContext(7L, 99L), fixture.modelClient, input());

        assertThat(decision.verdict()).isEqualTo(ReviewerVerdict.REJECT);
        assertThat(decision.findings()).singleElement()
                .satisfies(finding -> assertThat(finding.chunkId()).isEqualTo(55L));
        verify(fixture.persistence).markSucceeded(anyLong(), anyLong(), any());
    }

    @Test
    void shouldRejectReviewerAccessToAskLogTool() {
        Fixture fixture = fixture(
                toolCallResponse("search-1", SearchKnowledgeReadTool.NAME,
                        "{\"query\":\"transaction propagation\"}"),
                toolCallResponse("ask-1", GetAskLogEvidenceReadTool.NAME,
                        "{\"askLogId\":1}"));
        when(fixture.toolExecutor.execute(any(), any(), any()))
                .thenReturn(AgentMessage.toolResult("search-1", "{\"items\":[]}"));

        assertThatThrownBy(() -> fixture.agent.review(
                new AgentToolContext(7L, 99L), fixture.modelClient, input()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("tool is not allowed");
        verify(fixture.persistence).failRunIfActive(
                7L, 99L, "CHANGE_REVIEW_FAILED", "change reviewer orchestration failed");
    }

    @Test
    void shouldRejectHallucinatedChunkFinding() {
        Fixture fixture = fixture(
                toolCallResponse("search-1", SearchKnowledgeReadTool.NAME,
                        "{\"query\":\"transaction propagation\"}"),
                finalResponse("{\"verdict\":\"REVISE\","
                        + "\"summary\":\"A source must be considered.\","
                        + "\"findings\":[{\"code\":\"MISSING_SOURCE\","
                        + "\"severity\":\"WARNING\","
                        + "\"description\":\"Revise using the cited source.\","
                        + "\"evidencePath\":\"evidence\","
                        + "\"toolCallId\":\"search-1\",\"chunkId\":999}],"
                        + "\"confidence\":0.8}"));
        when(fixture.toolExecutor.execute(any(), any(), any()))
                .thenReturn(AgentMessage.toolResult(
                        "search-1", "{\"items\":[{\"chunkId\":55}]}"));

        assertThatThrownBy(() -> fixture.agent.review(
                new AgentToolContext(7L, 99L), fixture.modelClient, input()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("chunk absent");
    }

    private Fixture fixture(AgentModelResponse... responses) {
        AgentModelStepExecutor modelExecutor = mock(AgentModelStepExecutor.class);
        AgentToolCallExecutor toolExecutor = mock(AgentToolCallExecutor.class);
        AgentRunPersistenceService persistence = mock(AgentRunPersistenceService.class);
        AgentReadToolRegistry registry = mock(AgentReadToolRegistry.class);
        AgentModelClient modelClient = mock(AgentModelClient.class);
        when(registry.definitions()).thenReturn(List.of(
                definition(SearchKnowledgeReadTool.NAME),
                definition(GetChunkEvidenceReadTool.NAME),
                definition(GetAskLogEvidenceReadTool.NAME)));
        when(modelExecutor.execute(anyLong(), anyLong(), any(), any(), any()))
                .thenReturn(responses[0], java.util.Arrays.copyOfRange(responses, 1, responses.length));
        when(persistence.markSucceeded(anyLong(), anyLong(), any()))
                .thenReturn(AgentRunStatus.SUCCEEDED);
        ChangeReviewerAgent agent = new ChangeReviewerAgent(
                modelExecutor,
                toolExecutor,
                persistence,
                registry,
                new ReviewerDecisionCodec(objectMapper),
                objectMapper);
        return new Fixture(agent, toolExecutor, persistence, modelClient);
    }

    private AgentToolDefinition definition(String name) {
        return new AgentToolDefinition(name, name + " description", objectMapper.createObjectNode());
    }

    private AgentModelResponse toolCallResponse(String id, String name, String arguments) {
        return new AgentModelResponse(
                AgentMessage.assistantToolCalls(
                        null, List.of(AgentToolCall.function(id, name, arguments))),
                "tool_calls", "scripted:test", new AgentTokenUsage(5, 2, 7));
    }

    private AgentModelResponse finalResponse(String content) {
        return new AgentModelResponse(
                AgentMessage.assistant(content),
                "stop", "scripted:test", new AgentTokenUsage(5, 2, 7));
    }

    private ChangeReviewInput input() {
        return new ChangeReviewInput(
                10L, 20L, "knowledge_exists_not_retrieved", "{}", "METADATA_PATCH",
                31L, 1, "{\"tags\":\"spring,propagation\"}", "[]", "[]",
                "{}", "{}", "{}", 0);
    }

    private record Fixture(ChangeReviewerAgent agent,
                           AgentToolCallExecutor toolExecutor,
                           AgentRunPersistenceService persistence,
                           AgentModelClient modelClient) {
    }
}
