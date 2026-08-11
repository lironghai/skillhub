package com.iflytek.skillhub.workbench.application;

public record WorkbenchFileContent(String path, byte[] content, String contentType) {
    public WorkbenchFileContent {
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
