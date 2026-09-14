package com.iflytek.skillhub.mcp;

import com.fasterxml.jackson.databind.JsonNode;

public record McpAssociatedItemResponse(
        String id,
        String name,
        String description,
        JsonNode inputSchema,
        JsonNode outputSchema
) {
    public McpAssociatedItemResponse(String id, String name, String description) {
        this(id, name, description, null, null);
    }
}
