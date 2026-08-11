package com.iflytek.skillhub.workbench.poc;

import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.message.ToolUseBlock;
import java.util.List;
import java.util.Objects;

public final class MockMcpApprovalPolicy {

    public WorkbenchApprovalEvent evaluate(MockMcpToolRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.risk() == ToolRisk.READ_ONLY) {
            return new WorkbenchApprovalEvent(WorkbenchApprovalEvent.Type.ALLOWED, request, false, null);
        }
        ToolUseBlock toolCall = ToolUseBlock.builder()
                .id(request.serverName() + ":" + request.toolName())
                .name(request.toolName())
                .input(request.input())
                .build();
        RequireUserConfirmEvent confirmEvent = new RequireUserConfirmEvent("workbench-poc", List.of(toolCall));
        return new WorkbenchApprovalEvent(
                WorkbenchApprovalEvent.Type.APPROVAL_REQUIRED,
                request,
                false,
                confirmEvent);
    }
}
