package com.iflytek.skillhub.workbench.port;

import com.iflytek.skillhub.workbench.domain.WorkbenchMcpBinding;
import com.iflytek.skillhub.workbench.domain.WorkbenchMcpBindingStatus;
import java.util.List;
import java.util.Optional;

public interface WorkbenchMcpBindingRepository {
    WorkbenchMcpBinding save(WorkbenchMcpBinding binding);

    Optional<WorkbenchMcpBinding> findByIdAndSessionId(Long id, Long sessionId);

    List<WorkbenchMcpBinding> findBySessionId(Long sessionId);

    List<WorkbenchMcpBinding> findBySessionIdAndStatus(Long sessionId, WorkbenchMcpBindingStatus status);
}
