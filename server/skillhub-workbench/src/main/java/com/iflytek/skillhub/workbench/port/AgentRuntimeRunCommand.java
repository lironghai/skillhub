package com.iflytek.skillhub.workbench.port;

import com.iflytek.skillhub.workbench.domain.WorkbenchSession;
import java.util.List;

public record AgentRuntimeRunCommand(
        String userId,
        Long sessionId,
        String workspaceKey,
        String sessionContext,
        List<AgentRuntimeMcpServer> mcpServers,
        List<AgentRuntimeWorkspaceFile> workspaceFiles,
        String message) {
    public AgentRuntimeRunCommand(String userId, Long sessionId, String workspaceKey, String message) {
        this(userId, sessionId, workspaceKey, "", List.of(), List.of(), message);
    }

    public AgentRuntimeRunCommand(
            String userId,
            Long sessionId,
            String workspaceKey,
            String sessionContext,
            String message) {
        this(userId, sessionId, workspaceKey, sessionContext, List.of(), List.of(), message);
    }

    public AgentRuntimeRunCommand {
        WorkbenchSession.requireText(userId, "userId");
        WorkbenchSession.requirePositive(sessionId, "sessionId");
        WorkbenchSession.requireText(workspaceKey, "workspaceKey");
        sessionContext = sessionContext == null ? "" : sessionContext.trim();
        mcpServers = mcpServers == null ? List.of() : List.copyOf(mcpServers);
        workspaceFiles = workspaceFiles == null ? List.of() : List.copyOf(workspaceFiles);
        WorkbenchSession.requireText(message, "message");
    }
}
