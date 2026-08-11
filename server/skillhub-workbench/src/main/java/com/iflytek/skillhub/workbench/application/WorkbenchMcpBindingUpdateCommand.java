package com.iflytek.skillhub.workbench.application;

import java.util.List;

public record WorkbenchMcpBindingUpdateCommand(
        List<String> serverIds,
        List<WorkbenchMcpBindingSelection> bindings) {
}
