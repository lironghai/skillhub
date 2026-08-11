package com.iflytek.skillhub.workbench.application;

import java.util.List;

public record WorkbenchPublishValidationResult(
        boolean valid,
        List<String> errors,
        List<String> warnings,
        String resolvedSlug,
        String resolvedVersion) {
}
