package com.iflytek.skillhub.mcp;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;

public record McpInternalServerItemResponse(
        String id,
        String name,
        String description,
        String iconUrl,
        boolean enabled,
        String visibility,
        String ownerEmail,
        String team,
        int toolCount,
        int resourceCount,
        int promptCount,
        List<McpAssociatedItemResponse> tools,
        List<McpAssociatedItemResponse> resources,
        List<McpAssociatedItemResponse> prompts,
        List<String> tags,
        @JsonIgnore
        String streamableHttpUrl,
        @JsonIgnore
        String sseUrl
) {
    public McpInternalServerItemResponse(String id,
                                         String name,
                                         String description,
                                         boolean enabled,
                                         String visibility,
                                         String ownerEmail,
                                         String team,
                                         int toolCount,
                                         int resourceCount,
                                         int promptCount,
                                         List<McpAssociatedItemResponse> tools,
                                         List<McpAssociatedItemResponse> resources,
                                         List<McpAssociatedItemResponse> prompts,
                                         List<String> tags,
                                         String streamableHttpUrl,
                                         String sseUrl) {
        this(
                id,
                name,
                description,
                null,
                enabled,
                visibility,
                ownerEmail,
                team,
                toolCount,
                resourceCount,
                promptCount,
                tools,
                resources,
                prompts,
                tags,
                streamableHttpUrl,
                sseUrl
        );
    }
}
