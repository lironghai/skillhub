package com.iflytek.skillhub.workbench.application;

public record WorkbenchPackageFile(
        String path,
        long sizeBytes,
        String sha256) {
}
