package com.iflytek.skillhub.workbench.adapter;

import com.iflytek.skillhub.storage.ObjectStorageService;
import com.iflytek.skillhub.workbench.port.WorkbenchWorkspaceFile;
import com.iflytek.skillhub.workbench.port.WorkbenchWorkspaceStoragePort;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class WorkbenchWorkspaceStorageAdapter implements WorkbenchWorkspaceStoragePort {

    private final ObjectStorageService objectStorageService;

    public WorkbenchWorkspaceStorageAdapter(ObjectStorageService objectStorageService) {
        this.objectStorageService = objectStorageService;
    }

    @Override
    public void write(String workspaceKey, String relativePath, byte[] content, String contentType) {
        String key = storageKey(workspaceKey, relativePath);
        objectStorageService.putObject(key, new ByteArrayInputStream(content), content.length, contentType);
    }

    @Override
    public Optional<byte[]> read(String workspaceKey, String relativePath) {
        String key = storageKey(workspaceKey, relativePath);
        if (!objectStorageService.exists(key)) {
            return Optional.empty();
        }
        try (InputStream input = objectStorageService.getObject(key)) {
            return Optional.of(input.readAllBytes());
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read workbench workspace file", ex);
        }
    }

    @Override
    public List<WorkbenchWorkspaceFile> list(String workspaceKey) {
        throw new UnsupportedOperationException("Workbench file listing is snapshot-backed");
    }

    @Override
    public void delete(String workspaceKey, String relativePath) {
        objectStorageService.deleteObject(storageKey(workspaceKey, relativePath));
    }

    private String storageKey(String workspaceKey, String relativePath) {
        return workspaceKey + "/" + relativePath;
    }
}
