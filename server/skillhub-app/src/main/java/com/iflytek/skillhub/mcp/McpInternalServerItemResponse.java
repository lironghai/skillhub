package com.iflytek.skillhub.mcp;

import java.util.List;

public record McpInternalServerItemResponse(
        String id,
        String name,
        String description,
        boolean enabled,
        String visibility,
        String ownerEmail,
        String team,
        int toolCount,
        int resourceCount,
        int promptCount,
        List<String> tags,
        String streamableHttpUrl,
        String sseUrl
) {
}
