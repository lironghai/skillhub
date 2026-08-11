package com.iflytek.skillhub.workbench.port;

import com.iflytek.skillhub.workbench.domain.WorkbenchSession;
import java.net.URI;
import java.util.List;

public record AgentRuntimeMcpServer(
        String serverId,
        String transport,
        String url,
        List<String> enabledTools,
        List<String> disabledTools) {

    public AgentRuntimeMcpServer {
        WorkbenchSession.requireText(serverId, "serverId");
        transport = WorkbenchSession.requireText(transport, "transport").toLowerCase();
        if (!transport.equals("http") && !transport.equals("sse")) {
            throw new IllegalArgumentException("transport must be http or sse");
        }
        url = WorkbenchSession.requireText(url, "url");
        URI endpoint = URI.create(url);
        if (!endpoint.isAbsolute()
                || (!("http".equalsIgnoreCase(endpoint.getScheme()))
                && !("https".equalsIgnoreCase(endpoint.getScheme())))) {
            throw new IllegalArgumentException("MCP url must be an absolute HTTP(S) URL");
        }
        enabledTools = normalizedTools(enabledTools);
        disabledTools = normalizedTools(disabledTools);
    }

    private static List<String> normalizedTools(List<String> tools) {
        if (tools == null) {
            return List.of();
        }
        return tools.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }
}
