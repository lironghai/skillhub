package com.iflytek.skillhub.workbench.application;

public record WorkbenchPackagePublishResult(
        Long skillId,
        Long skillVersionId,
        String namespace,
        String slug,
        String version,
        String status) {
}
