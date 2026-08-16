package com.devmind.module.ai.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScriptedAgentModelClientTest {

    @Test
    void shouldReturnScriptedResponsesInOrderAndFailClearlyWhenExhausted() {
        AgentModelResponse toolCall = response(
                AgentMessage.assistantToolCalls(
                        null,
                        List.of(AgentToolCall.function("call-1", "search", "{\"query\":\"Redis\"}"))
                ),
                "tool_calls"
        );
        AgentModelResponse answer = response(AgentMessage.assistant("Done"), "stop");
        ScriptedAgentModelClient client = new ScriptedAgentModelClient(List.of(toolCall, answer));
        AgentModelRequest request = new AgentModelRequest(
                List.of(AgentMessage.user("Question")),
                List.of(),
                null
        );

        assertThat(client.supports("SCRIPTED")).isTrue();
        assertThat(client.complete(request)).isSameAs(toolCall);
        assertThat(client.complete(request)).isSameAs(answer);
        assertThat(client.consumedResponses()).isEqualTo(2);
        assertThatThrownBy(() -> client.complete(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exhausted at step 2");
    }

    private AgentModelResponse response(AgentMessage message, String finishReason) {
        return new AgentModelResponse(
                message,
                finishReason,
                "scripted:test",
                new AgentTokenUsage(null, null, null)
        );
    }
}
