package com.iflytek.skillhub.mcp;

public record SkillHubMcpSkillDetailResponse(
        Long skillId,
        String namespace,
        String slug,
        Long versionId,
        String version,
        String skillPath,
        String skillContent,
        String resolutionMode
) {}
