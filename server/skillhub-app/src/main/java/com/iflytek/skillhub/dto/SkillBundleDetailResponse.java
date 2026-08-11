package com.iflytek.skillhub.dto;

import java.time.Instant;
import java.util.List;

public record SkillBundleDetailResponse(
        Long id,
        String namespace,
        String slug,
        String name,
        String summary,
        String avatarUrl,
        String description,
        String roleDescription,
        String applicableScenarios,
        String methodology,
        String recommendedSkillNotes,
        String visibility,
        String status,
        boolean canManage,
        long downloadCount,
        List<SkillLabelDto> labels,
        List<SkillBundleItemResponse> items,
        Instant createdAt,
        Instant updatedAt
) {
}
