package com.iflytek.skillhub.mcp;

import java.util.List;

public record SkillHubMcpSkillRefResponse(
        Long skillId,
        String namespace,
        String slug,
        Long versionId,
        String version,
        List<SkillHubMcpReferenceFile> references,
        String resolutionMode
) {}
