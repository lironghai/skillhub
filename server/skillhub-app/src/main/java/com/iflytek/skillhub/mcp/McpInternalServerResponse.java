package com.iflytek.skillhub.mcp;

import java.util.List;

public record McpInternalServerResponse(
        List<McpInternalServerItemResponse> items,
        long total,
        int page,
        int size
) {
}
