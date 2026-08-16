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
public class TriageDiagnosisCodec {

    private static final Set<String> ROOT_FIELDS = Set.of(
            "rootCause", "summary", "evidence", "recommendedRoute", "confidence");
    private static final Set<String> EVIDENCE_FIELDS = Set.of(
            "toolCallId", "askLogId", "chunkId", "observation");

    private final ObjectMapper objectMapper;
    private final JsonNode schema;

    public TriageDiagnosisCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.schema = createSchema(objectMapper);
    }

    public JsonNode jsonSchema() {
        return schema.deepCopy();
    }

    public TriageDiagnosis parse(String content) {
        JsonNode root;
        try {
            root = objectMapper.readTree(content);
        } catch (JsonProcessingException ex) {
            throw badOutput("triage output is not valid JSON");
        }
        requireObjectWithOnly(root, ROOT_FIELDS, "triage output");
        TriageRootCause rootCause;
        TriageRoute route;
        try {
            rootCause = TriageRootCause.fromWireValue(requiredText(root, "rootCause", 64));
            route = TriageRoute.fromWireValue(requiredText(root, "recommendedRoute", 64));
        } catch (IllegalArgumentException ex) {
            throw badOutput(ex.getMessage());
        }
        String summary = requiredText(root, "summary", 1_000);
        JsonNode evidenceNode = root.get("evidence");
        if (evidenceNode == null || !evidenceNode.isArray()
                || evidenceNode.isEmpty() || evidenceNode.size() > 10) {
            throw badOutput("evidence must contain between 1 and 10 items");
        }
        List<TriageEvidence> evidence = new ArrayList<>();
        for (JsonNode item : evidenceNode) {
            requireObjectWithOnly(item, EVIDENCE_FIELDS, "triage evidence");
            try {
                evidence.add(new TriageEvidence(
                        requiredText(item, "toolCallId", 128),
                        optionalPositiveLong(item, "askLogId"),
                        optionalPositiveLong(item, "chunkId"),
                        requiredText(item, "observation", 500)
                ));
            } catch (IllegalArgumentException ex) {
                throw badOutput(ex.getMessage());
            }
        }
        JsonNode confidenceNode = root.get("confidence");
        if (confidenceNode == null || !confidenceNode.isNumber()) {
            throw badOutput("confidence must be a number between 0 and 1");
        }
        try {
            return new TriageDiagnosis(
                    rootCause, summary, evidence, route, confidenceNode.doubleValue());
        } catch (IllegalArgumentException ex) {
            throw badOutput(ex.getMessage());
        }
    }

    private static JsonNode createSchema(ObjectMapper mapper) {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        ObjectNode properties = schema.putObject("properties");
        ObjectNode causes = properties.putObject("rootCause").put("type", "string");
        for (TriageRootCause cause : TriageRootCause.values()) {
            causes.withArray("enum").add(cause.wireValue());
        }
        properties.putObject("summary")
                .put("type", "string").put("minLength", 1).put("maxLength", 1_000);
        ObjectNode evidence = properties.putObject("evidence");
        evidence.put("type", "array").put("minItems", 1).put("maxItems", 10);
        ObjectNode evidenceItem = evidence.putObject("items");
        evidenceItem.put("type", "object").put("additionalProperties", false);
        ObjectNode evidenceProperties = evidenceItem.putObject("properties");
        evidenceProperties.putObject("toolCallId")
                .put("type", "string").put("minLength", 1).put("maxLength", 128);
        evidenceProperties.putObject("askLogId").put("type", "integer").put("minimum", 1);
        evidenceProperties.putObject("chunkId").put("type", "integer").put("minimum", 1);
        evidenceProperties.putObject("observation")
                .put("type", "string").put("minLength", 1).put("maxLength", 500);
        evidenceItem.putArray("required").add("toolCallId").add("observation");
        ObjectNode routes = properties.putObject("recommendedRoute").put("type", "string");
        for (TriageRoute route : TriageRoute.values()) {
            routes.withArray("enum").add(route.wireValue());
        }
        properties.putObject("confidence")
                .put("type", "number").put("minimum", 0).put("maximum", 1);
        schema.putArray("required")
                .add("rootCause").add("summary").add("evidence")
                .add("recommendedRoute").add("confidence");
        return schema;
    }

    private static void requireObjectWithOnly(JsonNode node,
                                              Set<String> allowed,
                                              String label) {
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

    private static Long optionalPositiveLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() <= 0) {
            throw badOutput(field + " must be a positive integer");
        }
        return value.longValue();
    }

    private static BizException badOutput(String message) {
        return new BizException(ResultCode.BAD_REQUEST, message);
    }
}
