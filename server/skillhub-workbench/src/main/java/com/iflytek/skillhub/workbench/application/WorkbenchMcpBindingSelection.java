package com.iflytek.skillhub.workbench.application;

import java.util.List;

public record WorkbenchMcpBindingSelection(
        String serverId,
        List<String> enabledTools,
        List<String> disabledTools) {
}
