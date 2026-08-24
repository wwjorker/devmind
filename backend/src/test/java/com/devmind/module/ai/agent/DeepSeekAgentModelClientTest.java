package com.devmind.module.ai.agent;

import com.devmind.common.exception.BizException;
import com.devmind.module.ai.config.AiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DeepSeekAgentModelClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldEncodeFirstRoundAndParseToolCallResponse() throws Exception {
        AiProperties properties = deepSeekProperties();
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DeepSeekAgentModelClient client = new DeepSeekAgentModelClient(properties, builder);
        AgentModelRequest request = new AgentModelRequest(
                List.of(
                        AgentMessage.system("Use tools when evidence is needed."),
                        AgentMessage.user("Find Redis evidence")
                ),
                List.of(searchTool()),
                AgentToolChoice.auto()
        );

        server.expect(requestTo("https://api.example/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
                .andExpect(content().json("""
                        {
                          "model":"test-model",
                          "messages":[
                            {"role":"system","content":"Use tools when evidence is needed."},
                            {"role":"user","content":"Find Redis evidence"}
                          ],
                          "temperature":0.2,
                          "stream":false,
                          "thinking":{"type":"disabled"},
                          "tools":[{
                            "type":"function",
                            "function":{
                              "name":"search_knowledge",
                              "description":"Search the knowledge base",
                              "parameters":{
                                "type":"object",
                                "properties":{"query":{"type":"string"}},
                                "required":["query"]
                              }
                            }
                          }],
                          "tool_choice":"auto"
                        }
                        """, true))
                .andRespond(withSuccess("""
                        {
                          "model":"test-model",
                          "choices":[{
                            "finish_reason":"tool_calls",
                            "message":{
                              "role":"assistant",
                              "content":null,
                              "tool_calls":[{
                                "id":"call-search-1",
                                "type":"function",
                                "function":{
                                  "name":"search_knowledge",
                                  "arguments":"{\\"query\\":\\"Redis\\"}"
                                }
                              }]
                            }
                          }],
                          "usage":{"prompt_tokens":21,"completion_tokens":8,"total_tokens":29}
                        }
                        """, MediaType.APPLICATION_JSON));

        AgentModelResponse response = client.complete(request);

        assertThat(response.finishReason()).isEqualTo("tool_calls");
        assertThat(response.assistantMessage().content()).isNull();
        assertThat(response.assistantMessage().toolCalls()).containsExactly(
                AgentToolCall.function("call-search-1", "search_knowledge", "{\"query\":\"Redis\"}")
        );
        assertThat(response.usage()).isEqualTo(new AgentTokenUsage(21, 8, 29));
        server.verify();
    }

    @Test
    void shouldReconstructAssistantToolCallsAndMatchedToolResultsInSecondRound() throws Exception {
        AiProperties properties = deepSeekProperties();
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DeepSeekAgentModelClient client = new DeepSeekAgentModelClient(properties, builder);
        List<AgentToolCall> calls = List.of(
                AgentToolCall.function("call-1", "search_knowledge", "{\"query\":\"Redis\"}"),
                AgentToolCall.function("call-2", "search_knowledge", "{\"query\":\"MySQL\"}")
        );
        AgentModelRequest request = new AgentModelRequest(
                List.of(
                        AgentMessage.user("Compare Redis and MySQL"),
                        AgentMessage.assistantToolCalls(null, calls),
                        AgentMessage.toolResult("call-1", "Redis evidence"),
                        AgentMessage.toolResult("call-2", "MySQL evidence")
                ),
                List.of(searchTool()),
                AgentToolChoice.none()
        );

        server.expect(requestTo("https://api.example/v1/chat/completions"))
                .andExpect(content().json("""
                        {
                          "model":"test-model",
                          "messages":[
                            {"role":"user","content":"Compare Redis and MySQL"},
                            {
                              "role":"assistant",
                              "content":null,
                              "tool_calls":[
                                {
                                  "id":"call-1",
                                  "type":"function",
                                  "function":{"name":"search_knowledge","arguments":"{\\"query\\":\\"Redis\\"}"}
                                },
                                {
                                  "id":"call-2",
                                  "type":"function",
                                  "function":{"name":"search_knowledge","arguments":"{\\"query\\":\\"MySQL\\"}"}
                                }
                              ]
                            },
                            {"role":"tool","content":"Redis evidence","tool_call_id":"call-1"},
                            {"role":"tool","content":"MySQL evidence","tool_call_id":"call-2"}
                          ],
                          "temperature":0.2,
                          "stream":false,
                          "thinking":{"type":"disabled"},
                          "tools":[{
                            "type":"function",
                            "function":{
                              "name":"search_knowledge",
                              "description":"Search the knowledge base",
                              "parameters":{
                                "type":"object",
                                "properties":{"query":{"type":"string"}},
                                "required":["query"]
                              }
                            }
                          }],
                          "tool_choice":"none"
                        }
                        """, true))
                .andRespond(withSuccess("""
                        {
                          "choices":[{
                            "finish_reason":"stop",
                            "message":{"role":"assistant","content":"Redis is a cache; MySQL is relational."}
                          }],
                          "usage":{"prompt_tokens":21,"completion_tokens":7,"total_tokens":28}
                        }
                        """, MediaType.APPLICATION_JSON));

        AgentModelResponse response = client.complete(request);

        assertThat(response.assistantMessage().content())
                .isEqualTo("Redis is a cache; MySQL is relational.");
        assertThat(response.assistantMessage().toolCalls()).isEmpty();
        assertThat(response.usage()).isEqualTo(new AgentTokenUsage(21, 7, 28));
        server.verify();
    }

    @Test
    void shouldEncodeNamedToolChoice() throws Exception {
        DeepSeekAgentModelClient client = new DeepSeekAgentModelClient(deepSeekProperties());
        AgentModelRequest request = new AgentModelRequest(
                List.of(AgentMessage.user("Search")),
                List.of(searchTool()),
                AgentToolChoice.function("search_knowledge")
        );

        JsonNode requestJson = objectMapper.valueToTree(client.toWireRequest(request));

        assertThat(requestJson.path("tool_choice").path("type").asText()).isEqualTo("function");
        assertThat(requestJson.path("tool_choice").path("function").path("name").asText())
                .isEqualTo("search_knowledge");
    }

    @Test
    void shouldFailFastWhenApiKeyIsMissing() {
        DeepSeekAgentModelClient client = new DeepSeekAgentModelClient(new AiProperties());

        assertThatThrownBy(() -> client.complete(textOnlyRequest()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("API key is not configured");
    }

    @Test
    void shouldRejectMalformedToolCallEnvelope() {
        AiProperties properties = deepSeekProperties();
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DeepSeekAgentModelClient client = new DeepSeekAgentModelClient(properties, builder);

        server.expect(requestTo("https://api.example/v1/chat/completions"))
                .andRespond(withSuccess("""
                        {
                          "choices":[{
                            "finish_reason":"tool_calls",
                            "message":{"role":"assistant","content":null,"tool_calls":[]}
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.complete(textOnlyRequest()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("agent-model request failed");
        server.verify();
    }

    @Test
    void shouldPreserveProviderArgumentsAsRawTextForLaterValidation() throws Exception {
        DeepSeekAgentModelClient client = new DeepSeekAgentModelClient(deepSeekProperties());

        AgentModelResponse response = client.extractResponse(objectMapper.readTree("""
                {
                  "choices":[{
                    "finish_reason":"tool_calls",
                    "message":{
                      "role":"assistant",
                      "content":null,
                      "tool_calls":[{
                        "id":"call-invalid-json",
                        "type":"function",
                        "function":{"name":"search_knowledge","arguments":"not-json"}
                      }]
                    }
                  }],
                  "usage":{"prompt_tokens":10,"completion_tokens":3,"total_tokens":13}
                }
                """));

        assertThat(response.assistantMessage().toolCalls().get(0).arguments()).isEqualTo("not-json");
    }

    private AgentModelRequest textOnlyRequest() {
        return new AgentModelRequest(
                List.of(AgentMessage.user("Hello")),
                List.of(),
                null
        );
    }

    private AgentToolDefinition searchTool() throws Exception {
        return new AgentToolDefinition(
                "search_knowledge",
                "Search the knowledge base",
                objectMapper.readTree("""
                        {
                          "type":"object",
                          "properties":{"query":{"type":"string"}},
                          "required":["query"]
                        }
                        """)
        );
    }

    private AiProperties deepSeekProperties() {
        AiProperties properties = new AiProperties();
        properties.setDeepseekApiKey("test-key");
        properties.setDeepseekBaseUrl("https://api.example/v1");
        properties.setDeepseekModel("test-model");
        return properties;
    }
}
