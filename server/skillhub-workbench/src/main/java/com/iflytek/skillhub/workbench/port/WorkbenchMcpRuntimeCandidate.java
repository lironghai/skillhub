package com.iflytek.skillhub.workbench.port;

import com.iflytek.skillhub.workbench.domain.WorkbenchMcpCatalogSource;

public record WorkbenchMcpRuntimeCandidate(
        String serverId,
        WorkbenchMcpCatalogSource catalogSource,
        String runtimeEndpointRef,
        String streamableHttpUrl,
        String sseUrl) {

    public WorkbenchMcpRuntimeCandidate(
            String serverId,
            WorkbenchMcpCatalogSource catalogSource,
            String runtimeEndpointRef) {
        this(serverId, catalogSource, runtimeEndpointRef, null, null);
    }
}
