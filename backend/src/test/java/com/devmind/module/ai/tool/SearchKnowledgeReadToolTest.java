package com.devmind.module.ai.tool;

import com.devmind.common.exception.BizException;
import com.devmind.module.ai.agent.AgentToolContext;
import com.devmind.module.search.service.ChunkSearchService;
import com.devmind.module.search.vo.ChunkSearchResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchKnowledgeReadToolTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldUseServerSideUserIdAndBoundAgentLimit() throws Exception {
        ChunkSearchService searchService = mock(ChunkSearchService.class);
        SearchKnowledgeReadTool tool = new SearchKnowledgeReadTool(searchService, objectMapper);
        when(searchService.searchChunks(7L, "transaction propagation", 3))
                .thenReturn(List.of(new ChunkSearchResponse(
                        11L, 5L, "Spring notes", "java_note", "spring,tx",
                        0, "REQUIRES_NEW starts an independent transaction.", 23, 80)));

        JsonNode result = tool.execute(
                new AgentToolContext(7L, 99L),
                objectMapper.readTree("{\"query\":\"transaction propagation\",\"limit\":3}"));

        verify(searchService).searchChunks(7L, "transaction propagation", 3);
        assertThat(result.path("count").asInt()).isEqualTo(1);
        assertThat(result.path("items").path(0).path("chunkId").asLong()).isEqualTo(11L);
        assertThat(tool.definition().parameters().path("additionalProperties").asBoolean()).isFalse();
    }

    @Test
    void shouldRejectModelSuppliedTenantAndOversizedLimit() throws Exception {
        SearchKnowledgeReadTool tool = new SearchKnowledgeReadTool(
                mock(ChunkSearchService.class), objectMapper);

        assertThatThrownBy(() -> tool.execute(
                new AgentToolContext(7L, 99L),
                objectMapper.readTree("{\"query\":\"x\",\"userId\":8}")))
                .isInstanceOf(BizException.class)
                .hasMessage("tool arguments contain an unsupported field");
        assertThatThrownBy(() -> tool.execute(
                new AgentToolContext(7L, 99L),
                objectMapper.readTree("{\"query\":\"x\",\"limit\":6}")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("between 1 and 5");
    }
}
