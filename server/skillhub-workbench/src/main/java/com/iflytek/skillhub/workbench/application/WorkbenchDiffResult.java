package com.iflytek.skillhub.workbench.application;

import java.util.List;

public record WorkbenchDiffResult(List<WorkbenchFileDiff> files) {
    public WorkbenchDiffResult {
        files = List.copyOf(files);
    }
}
