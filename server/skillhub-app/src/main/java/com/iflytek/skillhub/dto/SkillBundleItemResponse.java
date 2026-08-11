package com.iflytek.skillhub.dto;

public record SkillBundleItemResponse(
        Long skillId,
        String namespace,
        String skillSlug,
        String displayName,
        Integer sortOrder,
        String note
) {
}
