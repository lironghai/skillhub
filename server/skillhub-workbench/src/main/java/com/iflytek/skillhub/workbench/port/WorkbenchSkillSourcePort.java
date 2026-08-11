package com.iflytek.skillhub.workbench.port;

import java.util.List;

public interface WorkbenchSkillSourcePort {
    List<WorkbenchSourceFile> listFiles(Long sourceVersionId);

    byte[] readFile(WorkbenchSourceFile file);
}
