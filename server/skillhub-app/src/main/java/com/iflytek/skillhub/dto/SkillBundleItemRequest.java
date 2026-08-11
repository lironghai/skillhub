package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SkillBundleItemRequest(
        @NotNull Long skillId,
        @Size(max = 2048) String note
) {
}
