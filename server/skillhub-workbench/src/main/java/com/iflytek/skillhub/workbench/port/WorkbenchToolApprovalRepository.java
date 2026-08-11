package com.iflytek.skillhub.workbench.port;

import com.iflytek.skillhub.workbench.domain.WorkbenchToolApproval;
import java.util.List;
import java.util.Optional;

public interface WorkbenchToolApprovalRepository {
    WorkbenchToolApproval save(WorkbenchToolApproval approval);

    Optional<WorkbenchToolApproval> findByIdAndSessionId(Long id, Long sessionId);

    Optional<WorkbenchToolApproval> findByEventId(Long eventId);

    List<WorkbenchToolApproval> findPendingBySessionId(Long sessionId);
}
