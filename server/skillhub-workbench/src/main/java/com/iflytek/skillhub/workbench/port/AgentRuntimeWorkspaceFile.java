package com.iflytek.skillhub.workbench.port;

import com.iflytek.skillhub.workbench.domain.WorkbenchFileSnapshot;
import java.util.Objects;

public record AgentRuntimeWorkspaceFile(
        String path,
        byte[] content,
        String contentType) {

    public AgentRuntimeWorkspaceFile {
        path = WorkbenchFileSnapshot.normalizeFilePath(path);
        content = Objects.requireNonNull(content, "content").clone();
        contentType = contentType == null || contentType.isBlank()
                ? "application/octet-stream"
                : contentType.trim();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
