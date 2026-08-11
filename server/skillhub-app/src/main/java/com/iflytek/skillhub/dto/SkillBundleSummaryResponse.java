package com.iflytek.skillhub.dto;

import java.time.Instant;
import java.util.List;

public record SkillBundleSummaryResponse(
        Long id,
        String namespace,
        String slug,
        String name,
        String summary,
        String avatarUrl,
        String roleDescription,
        String applicableScenarios,
        String visibility,
        String status,
        int skillCount,
        long downloadCount,
        List<SkillLabelDto> labels,
        Instant updatedAt
) {
}
