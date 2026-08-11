package com.iflytek.skillhub.workbench.port;

import java.util.List;
import java.util.Optional;

public interface WorkbenchWorkspaceStoragePort {
    void write(String workspaceKey, String relativePath, byte[] content, String contentType);

    Optional<byte[]> read(String workspaceKey, String relativePath);

    List<WorkbenchWorkspaceFile> list(String workspaceKey);

    void delete(String workspaceKey, String relativePath);
}
