package com.iflytek.skillhub.mcp;

import java.util.List;

public record McpCatalogItemResponse(
        String id,
        String name,
        String category,
        String provider,
        String description,
        String url,
        String authType,
        boolean requiresApiKey,
        boolean secure,
        List<String> tags,
        String transport,
        String logoUrl,
        String documentationUrl,
        boolean registered,
        boolean available,
        boolean requiresOauthConfig
) {
}
