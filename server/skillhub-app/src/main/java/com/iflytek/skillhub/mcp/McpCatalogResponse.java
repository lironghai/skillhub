package com.iflytek.skillhub.mcp;

import java.util.List;

public record McpCatalogResponse(
        List<McpCatalogItemResponse> items,
        long total,
        int page,
        int size,
        List<String> categories,
        List<String> authTypes,
        List<String> providers,
        List<String> tags
) {
}
