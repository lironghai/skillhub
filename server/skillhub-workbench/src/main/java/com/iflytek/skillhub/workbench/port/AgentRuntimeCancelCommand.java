package com.iflytek.skillhub.workbench.port;

import com.iflytek.skillhub.workbench.domain.WorkbenchSession;

public record AgentRuntimeCancelCommand(
        String userId,
        Long sessionId,
        String runId) {
    public AgentRuntimeCancelCommand {
        WorkbenchSession.requireText(userId, "userId");
        WorkbenchSession.requirePositive(sessionId, "sessionId");
        WorkbenchSession.requireText(runId, "runId");
    }
}
