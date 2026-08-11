package com.iflytek.skillhub.workbench.application;

import java.util.List;

public record ImportWorkbenchSourceResult(List<String> importedFiles) {
    public ImportWorkbenchSourceResult {
        importedFiles = List.copyOf(importedFiles);
    }
}
