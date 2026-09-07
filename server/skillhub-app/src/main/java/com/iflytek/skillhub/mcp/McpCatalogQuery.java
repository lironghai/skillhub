package com.iflytek.skillhub.mcp;

import java.util.List;

public record McpCatalogQuery(
        String search,
        String category,
        String authType,
        String provider,
        List<String> tags,
        int page,
        int size
) {
}
