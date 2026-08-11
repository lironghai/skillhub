package com.iflytek.skillhub.workbench.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

final class WorkbenchJsonValue {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private WorkbenchJsonValue() {
    }

    static String objectOrDefault(String json, String fieldName) {
        return validate(json, fieldName, true, "{}");
    }

    static String arrayOrDefault(String json, String fieldName) {
        return validate(json, fieldName, false, "[]");
    }

    private static String validate(String json, String fieldName, boolean objectExpected, String defaultValue) {
        if (json == null || json.isBlank()) {
            return defaultValue;
        }
        JsonNode node;
        try {
            node = OBJECT_MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(fieldName + " must be valid JSON", e);
        }
        if (objectExpected && !node.isObject()) {
            throw new IllegalArgumentException(fieldName + " must be a JSON object");
        }
        if (!objectExpected && !node.isArray()) {
            throw new IllegalArgumentException(fieldName + " must be a JSON array");
        }
        return json;
    }
}
