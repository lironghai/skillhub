package com.iflytek.skillhub.workbench.adapter;

import com.iflytek.skillhub.mcp.McpCatalogService;
import com.iflytek.skillhub.mcp.McpInternalServerItemResponse;
import com.iflytek.skillhub.workbench.domain.WorkbenchMcpCatalogSource;
import com.iflytek.skillhub.workbench.port.WorkbenchMcpCatalogPort;
import com.iflytek.skillhub.workbench.port.WorkbenchMcpRuntimeCandidate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
class WorkbenchMcpCatalogAdapter implements WorkbenchMcpCatalogPort {

    private static final String WORKBENCH_POLICY_VERSION_PREFIX = "context-forge:";

    private final McpCatalogService catalogService;

    WorkbenchMcpCatalogAdapter(McpCatalogService catalogService) {
        this.catalogService = Objects.requireNonNull(catalogService, "catalogService");
    }

    @Override
    public List<WorkbenchMcpRuntimeCandidate> resolveRuntimeCandidates(List<String> serverIds) {
        LinkedHashSet<String> requested = new LinkedHashSet<>(serverIds == null ? List.of() : serverIds);
        List<WorkbenchMcpRuntimeCandidate> candidates = new ArrayList<>();
        for (String serverId : requested) {
            McpInternalServerItemResponse item = catalogService.internalServers(serverId, "0", "50")
                    .items()
                    .stream()
                    .filter(candidate -> serverId.equals(candidate.id()))
                    .findFirst()
                    .orElse(null);
            if (item != null && item.enabled()) {
                candidates.add(new WorkbenchMcpRuntimeCandidate(
                        item.id(),
                        WorkbenchMcpCatalogSource.CONTEXT_FORGE,
                        WORKBENCH_POLICY_VERSION_PREFIX + item.id(),
                        item.streamableHttpUrl(),
                        item.sseUrl()));
            }
        }
        return candidates;
    }
}
