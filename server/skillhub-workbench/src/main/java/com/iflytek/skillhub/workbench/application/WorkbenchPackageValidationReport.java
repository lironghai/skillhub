package com.iflytek.skillhub.workbench.application;

import java.util.List;

public record WorkbenchPackageValidationReport(
        String status,
        List<String> messages,
        List<String> errors,
        List<String> warnings,
        String resolvedSlug,
        String resolvedVersion) {
}
