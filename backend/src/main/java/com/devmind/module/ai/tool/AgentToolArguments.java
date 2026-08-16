package com.devmind.module.ai.tool;

import com.devmind.common.api.ResultCode;
import com.devmind.common.exception.BizException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class AgentToolArguments {

    private AgentToolArguments() {
    }

    static void requireOnly(JsonNode arguments, String... allowedFields) {
        if (arguments == null || !arguments.isObject()) {
            throw badRequest("tool arguments must be a JSON object");
        }
        Set<String> allowed = new HashSet<>(List.of(allowedFields));
        Iterator<String> fields = arguments.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (!allowed.contains(field)) {
                throw badRequest("tool arguments contain an unsupported field");
            }
        }
    }

    static String requiredText(JsonNode arguments, String field, int maxLength) {
        JsonNode value = arguments.get(field);
        if (value == null || !value.isTextual() || !StringUtils.hasText(value.textValue())) {
            throw badRequest(field + " must be a non-blank string");
        }
        String normalized = value.textValue().trim();
        if (normalized.length() > maxLength) {
            throw badRequest(field + " is too long");
        }
        return normalized;
    }

    static int optionalInt(JsonNode arguments, String field, int defaultValue, int min, int max) {
        JsonNode value = arguments.get(field);
        if (value == null) {
            return defaultValue;
        }
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw badRequest(field + " must be an integer");
        }
        int number = value.intValue();
        if (number < min || number > max) {
            throw badRequest(field + " must be between " + min + " and " + max);
        }
        return number;
    }

    static long requiredPositiveLong(JsonNode arguments, String field) {
        JsonNode value = arguments.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()
                || value.longValue() <= 0) {
            throw badRequest(field + " must be a positive integer");
        }
        return value.longValue();
    }

    static List<Long> requiredPositiveLongArray(JsonNode arguments, String field, int maxItems) {
        JsonNode value = arguments.get(field);
        if (value == null || !value.isArray() || value.isEmpty() || value.size() > maxItems) {
            throw badRequest(field + " must contain between 1 and " + maxItems + " ids");
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (JsonNode item : value) {
            if (!item.isIntegralNumber() || !item.canConvertToLong() || item.longValue() <= 0) {
                throw badRequest(field + " must contain only positive integer ids");
            }
            ids.add(item.longValue());
        }
        return new ArrayList<>(ids);
    }

    static BizException badRequest(String message) {
        return new BizException(ResultCode.BAD_REQUEST, message);
    }
}
