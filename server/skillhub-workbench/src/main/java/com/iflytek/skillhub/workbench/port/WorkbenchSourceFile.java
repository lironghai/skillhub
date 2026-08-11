package com.iflytek.skillhub.workbench.port;

public record WorkbenchSourceFile(String path, long sizeBytes, String contentType, String storageKey) {
}
