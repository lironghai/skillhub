package com.iflytek.skillhub.workbench.poc;

import io.agentscope.core.event.RequireUserConfirmEvent;

public record WorkbenchApprovalEvent(
        Type type,
        MockMcpToolRequest request,
        boolean executed,
        RequireUserConfirmEvent agentScopeEvent) {

    public enum Type {
        ALLOWED,
        APPROVAL_REQUIRED
    }
}
