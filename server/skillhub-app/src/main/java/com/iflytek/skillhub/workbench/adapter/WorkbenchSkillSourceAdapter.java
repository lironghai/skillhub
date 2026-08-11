package com.iflytek.skillhub.workbench.adapter;

import com.iflytek.skillhub.domain.skill.SkillFile;
import com.iflytek.skillhub.domain.skill.SkillFileRepository;
import com.iflytek.skillhub.storage.ObjectStorageService;
import com.iflytek.skillhub.workbench.port.WorkbenchSkillSourcePort;
import com.iflytek.skillhub.workbench.port.WorkbenchSourceFile;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class WorkbenchSkillSourceAdapter implements WorkbenchSkillSourcePort {

    private final SkillFileRepository skillFileRepository;
    private final ObjectStorageService objectStorageService;

    public WorkbenchSkillSourceAdapter(
            SkillFileRepository skillFileRepository,
            ObjectStorageService objectStorageService) {
        this.skillFileRepository = skillFileRepository;
        this.objectStorageService = objectStorageService;
    }

    @Override
    public List<WorkbenchSourceFile> listFiles(Long sourceVersionId) {
        return skillFileRepository.findByVersionId(sourceVersionId).stream()
                .map(file -> new WorkbenchSourceFile(
                        file.getFilePath(),
                        file.getFileSize(),
                        file.getContentType(),
                        file.getStorageKey()))
                .toList();
    }

    @Override
    public byte[] readFile(WorkbenchSourceFile file) {
        try (InputStream input = objectStorageService.getObject(file.storageKey())) {
            return input.readAllBytes();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read source skill file", ex);
        }
    }
}
