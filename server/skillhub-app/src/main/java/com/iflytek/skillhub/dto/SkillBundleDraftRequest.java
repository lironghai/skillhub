package com.iflytek.skillhub.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SkillBundleDraftRequest(
        @NotBlank String namespace,
        @NotBlank String name,
        @NotBlank String slug,
        String summary,
        @Size(max = 2048) String avatarUrl,
        String description,
        String roleDescription,
        String applicableScenarios,
        String methodology,
        String recommendedSkillNotes,
        String visibility,
        String status,
        List<String> labels,
        @Valid List<SkillBundleItemRequest> items
) {
}
