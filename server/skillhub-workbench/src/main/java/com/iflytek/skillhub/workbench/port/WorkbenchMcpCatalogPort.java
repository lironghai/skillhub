package com.iflytek.skillhub.workbench.port;

import java.util.List;

public interface WorkbenchMcpCatalogPort {
    List<WorkbenchMcpRuntimeCandidate> resolveRuntimeCandidates(List<String> serverIds);
}
