package com.iflytek.skillhub.workbench.application;

import java.util.List;

public record WorkbenchPackagePreviewResult(
        String packageFingerprint,
        boolean readyToPublish,
        List<WorkbenchPackageFile> includedFiles,
        List<WorkbenchPackageExcludedFile> excludedFiles,
        WorkbenchPackageValidationReport validation) {
}
