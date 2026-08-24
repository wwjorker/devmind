package com.devmind.module.ai.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devmind.common.api.ResultCode;
import com.devmind.common.exception.BizException;
import com.devmind.module.ai.agent.AgentReadTool;
import com.devmind.module.ai.agent.AgentToolContext;
import com.devmind.module.ai.agent.AgentToolDefinition;
import com.devmind.module.ai.entity.AiAskLog;
import com.devmind.module.ai.mapper.AiAskLogMapper;
import com.devmind.module.ai.service.PromptSchemaVersions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
public class GetAskLogEvidenceReadTool implements AgentReadTool {

    public static final String NAME = "getAskLogEvidence";
    private static final int MAX_ANSWER_CHARS = 4_000;

    private final AiAskLogMapper askLogMapper;
    private final ObjectMapper objectMapper;
    private final AgentToolDefinition definition;

    public GetAskLogEvidenceReadTool(AiAskLogMapper askLogMapper, ObjectMapper objectMapper) {
        this.askLogMapper = askLogMapper;
        this.objectMapper = objectMapper;
        this.definition = new AgentToolDefinition(
                NAME,
                "Read one owned ask log and its recorded citation ids for evidence triage.",
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
        AgentToolArguments.requireOnly(arguments, "askLogId");
        long askLogId = AgentToolArguments.requiredPositiveLong(arguments, "askLogId");
        AiAskLog log = askLogMapper.selectOne(new LambdaQueryWrapper<AiAskLog>()
                .eq(AiAskLog::getId, askLogId)
                .eq(AiAskLog::getUserId, context.userId()));
        if (log == null) {
            throw new BizException(ResultCode.NOT_FOUND, "ask log not found");
        }

        ObjectNode result = objectMapper.createObjectNode();
        result.put("askLogId", log.getId());
        result.put("question", log.getQuestion());
        result.put("retrievalKeyword", log.getRetrievalKeyword());
        String answer = log.getAnswer() == null ? "" : log.getAnswer();
        result.put("answer", truncate(answer, MAX_ANSWER_CHARS));
        result.put("answerTruncated", answer.length() > MAX_ANSWER_CHARS);
        putNullable(result, "modelProvider", log.getModelProvider());
        putNullable(result, "promptSchemaVersion", log.getPromptSchemaVersion());
        boolean eligible = PromptSchemaVersions.isAnswerGroundingEvaluationEligible(
                log.getPromptSchemaVersion());
        result.put("answerGroundingEvaluationEligible", eligible);
        result.put("eligibilityNote", eligible
                ? "Recorded citations match the full-prompt schema, but chunk rows are not immutable snapshots."
                : "Legacy or unknown prompt schema must not be used to judge correct-evidence/wrong-answer cases.");
        result.put("promptPreviewIsComplete", false);
        putNullable(result, "promptPreview", log.getPromptPreview());
        putNullable(result, "retrievedChunkCount", log.getRetrievedChunkCount());
        ArrayNode ids = result.putArray("retrievedChunkIds");
        if (StringUtils.hasText(log.getRetrievedChunkIds())) {
            for (String value : log.getRetrievedChunkIds().split(",")) {
                try {
                    long id = Long.parseLong(value.trim());
                    if (id > 0) ids.add(id);
                } catch (NumberFormatException ignored) {
                    // Preserve tool availability for an old malformed log; count mismatch stays visible.
                }
            }
        }
        putNullable(result, "status", log.getStatus());
        putNullable(result, "createdAt", log.getCreatedAt() == null ? null : log.getCreatedAt().toString());
        return result;
    }

    private static ObjectNode schema(ObjectMapper mapper) {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.putObject("properties").putObject("askLogId")
                .put("type", "integer")
                .put("minimum", 1);
        schema.putArray("required").add("askLogId");
        schema.put("additionalProperties", false);
        return schema;
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static void putNullable(ObjectNode node, String field, String value) {
        if (value == null) node.putNull(field); else node.put(field, value);
    }

    private static void putNullable(ObjectNode node, String field, Integer value) {
        if (value == null) node.putNull(field); else node.put(field, value);
    }
}
