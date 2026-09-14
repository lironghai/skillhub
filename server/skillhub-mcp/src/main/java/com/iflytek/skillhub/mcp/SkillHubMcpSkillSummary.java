package com.iflytek.skillhub.mcp;

import java.math.BigDecimal;
import java.time.Instant;

public record SkillHubMcpSkillSummary(
        Long skillId,
        String namespace,
        String slug,
        String displayName,
        String summary,
        String visibility,
        String status,
        String headlineVersion,
        String publishedVersion,
        Instant updatedAt,
        Long downloadCount,
        BigDecimal ratingAvg,
        Integer ratingCount,
        String resolutionMode
) {}
