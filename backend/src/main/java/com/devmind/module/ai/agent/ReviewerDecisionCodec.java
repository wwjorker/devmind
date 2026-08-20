package com.devmind.module.ai.agent;

import com.devmind.common.api.ResultCode;
import com.devmind.common.exception.BizException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

@Component
public class ReviewerDecisionCodec {

    private static final Set<String> ROOT_FIELDS =
            Set.of("verdict", "summary", "findings", "confidence");
    private static final Set<String> FINDING_FIELDS = Set.of(
            "code", "severity", "description", "evidencePath", "toolCallId", "chunkId");

    private final ObjectMapper objectMapper;
    private final JsonNode schema;

    public ReviewerDecisionCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.schema = createSchema(objectMapper);
    }

    public JsonNode jsonSchema() {
        return schema.deepCopy();
    }

    public ReviewerDecision parse(String content) {
        JsonNode root;
        try {
            root = objectMapper.readTree(content);
        } catch (JsonProcessingException ex) {
            throw badOutput("reviewer output is not valid JSON");
        }
        requireObjectWithOnly(root, ROOT_FIELDS, "reviewer output");
        ReviewerVerdict verdict = enumValue(
                ReviewerVerdict.class, requiredText(root, "verdict", 16), "verdict");
        String summary = requiredText(root, "summary", 1_000);
        JsonNode findingsNode = root.get("findings");
        if (findingsNode == null || !findingsNode.isArray() || findingsNode.size() > 10) {
            throw badOutput("findings must be an array with at most 10 items");
        }
        List<ReviewerFinding> findings = new ArrayList<>();
        for (JsonNode item : findingsNode) {
            requireObjectWithOnly(item, FINDING_FIELDS, "reviewer finding");
            try {
                findings.add(new ReviewerFinding(
                        requiredText(item, "code", 64),
                        enumValue(ReviewerSeverity.class,
                                requiredText(item, "severity", 16), "severity"),
                        requiredText(item, "description", 1_000),
                        requiredText(item, "evidencePath", 256),
                        optionalText(item, "toolCallId", 128),
                        optionalPositiveLong(item, "chunkId")));
            } catch (IllegalArgumentException ex) {
                throw badOutput(ex.getMessage());
            }
        }
        JsonNode confidence = root.get("confidence");
        if (confidence == null || !confidence.isNumber()) {
            throw badOutput("confidence must be a number between 0 and 1");
        }
        try {
            return new ReviewerDecision(
                    verdict, summary, findings, confidence.doubleValue());
        } catch (IllegalArgumentException ex) {
            throw badOutput(ex.getMessage());
        }
    }

    private static JsonNode createSchema(ObjectMapper mapper) {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object").put("additionalProperties", false);
        ObjectNode properties = schema.putObject("properties");
        enumSchema(properties.putObject("verdict"), ReviewerVerdict.values());
        properties.putObject("summary").put("type", "string")
                .put("minLength", 1).put("maxLength", 1_000);
        ObjectNode findings = properties.putObject("findings");
        findings.put("type", "array").put("maxItems", 10);
        ObjectNode item = findings.putObject("items");
        item.put("type", "object").put("additionalProperties", false);
        ObjectNode fields = item.putObject("properties");
        fields.putObject("code").put("type", "string").put("minLength", 1).put("maxLength", 64);
        enumSchema(fields.putObject("severity"), ReviewerSeverity.values());
        fields.putObject("description").put("type", "string")
                .put("minLength", 1).put("maxLength", 1_000);
        fields.putObject("evidencePath").put("type", "string")
                .put("minLength", 1).put("maxLength", 256);
        fields.putObject("toolCallId").put("type", "string").put("maxLength", 128);
        fields.putObject("chunkId").put("type", "integer").put("minimum", 1);
        item.putArray("required")
                .add("code").add("severity").add("description").add("evidencePath");
        properties.putObject("confidence").put("type", "number")
                .put("minimum", 0).put("maximum", 1);
        schema.putArray("required")
                .add("verdict").add("summary").add("findings").add("confidence");
        return schema;
    }

    private static void enumSchema(ObjectNode node, Enum<?>[] values) {
        node.put("type", "string");
        for (Enum<?> value : values) {
            node.withArray("enum").add(value.name());
        }
    }

    private static void requireObjectWithOnly(JsonNode node, Set<String> allowed, String label) {
        if (node == null || !node.isObject()) {
            throw badOutput(label + " must be a JSON object");
        }
        Set<String> unknown = new HashSet<>();
        Iterator<String> fields = node.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (!allowed.contains(field)) unknown.add(field);
        }
        if (!unknown.isEmpty()) {
            throw badOutput(label + " contains unsupported fields: " + unknown);
        }
    }

    private static String requiredText(JsonNode node, String field, int maxLength) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()
                || value.textValue().length() > maxLength) {
            throw badOutput(field + " must be a non-blank bounded string");
        }
        return value.textValue().trim();
    }

    private static String optionalText(JsonNode node, String field, int maxLength) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual() || value.textValue().isBlank()
                || value.textValue().length() > maxLength) {
            throw badOutput(field + " must be a bounded string");
        }
        return value.textValue().trim();
    }

    private static Long optionalPositiveLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() <= 0) {
            throw badOutput(field + " must be a positive integer");
        }
        return value.longValue();
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, String field) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException ex) {
            throw badOutput("unsupported " + field + ": " + value);
        }
    }

    private static BizException badOutput(String message) {
        return new BizException(ResultCode.BAD_REQUEST, message);
    }
}
