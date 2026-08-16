package com.devmind.module.ai.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devmind.module.ai.agent.AgentReadTool;
import com.devmind.module.ai.agent.AgentToolContext;
import com.devmind.module.ai.agent.AgentToolDefinition;
import com.devmind.module.document.entity.DocumentChunk;
import com.devmind.module.document.entity.KnowledgeDocument;
import com.devmind.module.document.mapper.DocumentChunkMapper;
import com.devmind.module.document.mapper.KnowledgeDocumentMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class GetChunkEvidenceReadTool implements AgentReadTool {

    public static final String NAME = "getChunkEvidence";
    private static final int MAX_CHUNKS = 10;

    private final DocumentChunkMapper chunkMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final ObjectMapper objectMapper;
    private final AgentToolDefinition definition;

    public GetChunkEvidenceReadTool(DocumentChunkMapper chunkMapper,
                                    KnowledgeDocumentMapper documentMapper,
                                    ObjectMapper objectMapper) {
        this.chunkMapper = chunkMapper;
        this.documentMapper = documentMapper;
        this.objectMapper = objectMapper;
        this.definition = new AgentToolDefinition(
                NAME,
                "Read current or archived chunks by id for historical evidence inspection.",
                schema(objectMapper)
        );
    }

    @Override
    public AgentToolDefinition definition() {
        return definition;
    }

    @Override
    @Transactional(readOnly = true)
    public JsonNode execute(AgentToolContext context, JsonNode arguments) {
        AgentToolArguments.requireOnly(arguments, "chunkIds");
        List<Long> chunkIds = AgentToolArguments.requiredPositiveLongArray(
                arguments, "chunkIds", MAX_CHUNKS);
        List<DocumentChunk> chunks = chunkMapper.selectList(new LambdaQueryWrapper<DocumentChunk>()
                .eq(DocumentChunk::getUserId, context.userId())
                .in(DocumentChunk::getId, chunkIds));
        Set<Long> documentIds = chunks.stream()
                .map(DocumentChunk::getDocumentId)
                .collect(Collectors.toSet());
        Map<Long, KnowledgeDocument> documents = documentIds.isEmpty()
                ? Map.of()
                : documentMapper.selectList(new LambdaQueryWrapper<KnowledgeDocument>()
                        .eq(KnowledgeDocument::getUserId, context.userId())
                        .in(KnowledgeDocument::getId, documentIds))
                .stream().collect(Collectors.toMap(KnowledgeDocument::getId, Function.identity()));
        Map<Long, DocumentChunk> ownedChunks = chunks.stream()
                .filter(chunk -> documents.containsKey(chunk.getDocumentId()))
                .collect(Collectors.toMap(DocumentChunk::getId, Function.identity()));

        ObjectNode result = objectMapper.createObjectNode();
        result.put("historicalSnapshotGuaranteed", false);
        result.put("snapshotNote",
                "Rows are resolved now from active or archived MySQL data; they are not immutable historical snapshots.");
        ArrayNode items = result.putArray("items");
        ArrayNode notFound = result.putArray("notFoundChunkIds");
        for (Long chunkId : chunkIds) {
            DocumentChunk chunk = ownedChunks.get(chunkId);
            if (chunk == null) {
                notFound.add(chunkId);
                continue;
            }
            KnowledgeDocument document = documents.get(chunk.getDocumentId());
            ObjectNode item = items.addObject();
            item.put("chunkId", chunk.getId());
            item.put("documentId", chunk.getDocumentId());
            item.put("documentTitle", document.getTitle());
            item.put("sourceType", document.getSourceType());
            putNullable(item, "tags", document.getTags());
            item.put("chunkIndex", chunk.getChunkIndex());
            item.put("content", chunk.getContent());
            item.put("tokenCount", chunk.getTokenCount());
            item.put("chunkStatus", statusName(chunk.getStatus()));
            item.put("documentStatus", statusName(document.getStatus()));
        }
        result.put("count", items.size());
        return result;
    }

    private static ObjectNode schema(ObjectMapper mapper) {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode ids = schema.putObject("properties").putObject("chunkIds");
        ids.put("type", "array");
        ids.put("minItems", 1);
        ids.put("maxItems", MAX_CHUNKS);
        ids.putObject("items").put("type", "integer").put("minimum", 1);
        schema.putArray("required").add("chunkIds");
        schema.put("additionalProperties", false);
        return schema;
    }

    private static String statusName(Integer status) {
        return status != null && status == 1 ? "ACTIVE" : "ARCHIVED";
    }

    private static void putNullable(ObjectNode node, String field, String value) {
        if (value == null) node.putNull(field); else node.put(field, value);
    }
}
