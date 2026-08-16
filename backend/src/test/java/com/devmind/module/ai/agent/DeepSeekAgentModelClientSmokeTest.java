package com.devmind.module.ai.agent;

import com.devmind.module.ai.config.AiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "DEVMIND_RUN_DEEPSEEK_SMOKE", matches = "(?i)true")
@EnabledIfEnvironmentVariable(named = "DEVMIND_DEEPSEEK_API_KEY", matches = ".+")
class DeepSeekAgentModelClientSmokeTest {

    @Test
    void shouldCompleteARealTwoRoundToolCallWithoutExposingCredentials() throws Exception {
        AiProperties properties = new AiProperties();
        properties.setDeepseekApiKey(System.getenv("DEVMIND_DEEPSEEK_API_KEY"));
        setIfPresent(System.getenv("DEVMIND_DEEPSEEK_BASE_URL"), properties::setDeepseekBaseUrl);
        setIfPresent(System.getenv("DEVMIND_DEEPSEEK_MODEL"), properties::setDeepseekModel);
        DeepSeekAgentModelClient client = new DeepSeekAgentModelClient(properties);
        AgentToolDefinition echoTool = new AgentToolDefinition(
                "echo_word",
                "Return a supplied word unchanged",
                new ObjectMapper().readTree("""
                        {
                          "type":"object",
                          "properties":{"word":{"type":"string"}},
                          "required":["word"]
                        }
                        """)
        );
        List<AgentMessage> history = new ArrayList<>();
        history.add(AgentMessage.system("Follow the requested tool protocol exactly."));
        history.add(AgentMessage.user("Call echo_word with the word Redis."));

        AgentModelResponse toolRound = client.complete(new AgentModelRequest(
                history,
                List.of(echoTool),
                AgentToolChoice.function("echo_word")
        ));

        assertThat(toolRound.finishReason()).isEqualTo("tool_calls");
        assertThat(toolRound.assistantMessage().toolCalls()).hasSize(1);
        AgentToolCall call = toolRound.assistantMessage().toolCalls().get(0);
        assertThat(call.name()).isEqualTo("echo_word");
        history.add(toolRound.assistantMessage());
        history.add(AgentMessage.toolResult(call.id(), "Redis"));

        AgentModelResponse answerRound = client.complete(new AgentModelRequest(
                history,
                List.of(echoTool),
                AgentToolChoice.none()
        ));

        assertThat(answerRound.finishReason()).isEqualTo("stop");
        assertThat(answerRound.assistantMessage().content()).containsIgnoringCase("Redis");
    }

    private void setIfPresent(String value, java.util.function.Consumer<String> setter) {
        if (value != null && !value.isBlank()) {
            setter.accept(value);
        }
    }
}
