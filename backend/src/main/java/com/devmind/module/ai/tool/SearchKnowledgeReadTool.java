package com.devmind.module.ai.tool;

import com.devmind.module.ai.agent.AgentReadTool;
import com.devmind.module.ai.agent.AgentToolContext;
import com.devmind.module.ai.agent.AgentToolDefinition;
import com.devmind.module.search.service.ChunkSearchService;
import com.devmind.module.search.vo.ChunkSearchResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SearchKnowledgeReadTool implements AgentReadTool {

    public static final String NAME = "searchKnowledge";
    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 5;

    private final ChunkSearchService searchService;
    private final ObjectMapper objectMapper;
    private final AgentToolDefinition definition;

    public SearchKnowledgeReadTool(ChunkSearchService searchService, ObjectMapper objectMapper) {
        this.searchService = searchService;
        this.objectMapper = objectMapper;
        this.definition = new AgentToolDefinition(
                NAME,
                "Search active knowledge-base chunks owned by the current user.",
                schema(objectMapper)
        );
    }

    @Override
    public AgentToolDefinition definition() {
        return definition;
    }

    @Override
    public JsonNode execute(AgentToolContext context, JsonNode arguments) {
        AgentToolArguments.requireOnly(arguments, "query", "limit");
        String query = AgentToolArguments.requiredText(arguments, "query", 500);
        int limit = AgentToolArguments.optionalInt(
                arguments, "limit", DEFAULT_LIMIT, 1, MAX_LIMIT);
        List<ChunkSearchResponse> matches = searchService.searchChunks(
                context.userId(), query, limit);

        ObjectNode result = objectMapper.createObjectNode();
        result.put("query", query);
        result.put("count", matches.size());
        ArrayNode items = result.putArray("items");
        for (ChunkSearchResponse match : matches) {
            ObjectNode item = items.addObject();
            putLong(item, "chunkId", match.getChunkId());
            putLong(item, "documentId", match.getDocumentId());
            putText(item, "documentTitle", match.getDocumentTitle());
            putText(item, "sourceType", match.getSourceType());
            putText(item, "tags", match.getTags());
            putInt(item, "chunkIndex", match.getChunkIndex());
            putText(item, "content", match.getContent());
            putInt(item, "tokenCount", match.getTokenCount());
            putInt(item, "score", match.getScore());
        }
        return result;
    }

    private static ObjectNode schema(ObjectMapper mapper) {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("query")
                .put("type", "string")
                .put("minLength", 1)
                .put("maxLength", 500);
        properties.putObject("limit")
                .put("type", "integer")
                .put("minimum", 1)
                .put("maximum", MAX_LIMIT);
        schema.putArray("required").add("query");
        schema.put("additionalProperties", false);
        return schema;
    }

    private static void putText(ObjectNode node, String field, String value) {
        if (value == null) node.putNull(field); else node.put(field, value);
    }

    private static void putLong(ObjectNode node, String field, Long value) {
        if (value == null) node.putNull(field); else node.put(field, value);
    }

    private static void putInt(ObjectNode node, String field, Integer value) {
        if (value == null) node.putNull(field); else node.put(field, value);
    }
}
