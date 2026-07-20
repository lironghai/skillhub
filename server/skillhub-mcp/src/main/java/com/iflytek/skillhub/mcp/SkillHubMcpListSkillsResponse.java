package com.iflytek.skillhub.mcp;

import java.util.List;

public record SkillHubMcpListSkillsResponse(
        List<SkillHubMcpSkillSummary> items,
        long total,
        int page,
        int size
) {}
