package com.iflytek.skillhub.workbench.port;

import com.iflytek.skillhub.workbench.domain.WorkbenchSession;
import com.iflytek.skillhub.workbench.domain.WorkbenchSessionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WorkbenchSessionRepository {
    Optional<WorkbenchSession> findById(Long id);

    Optional<WorkbenchSession> findByIdAndUserId(Long id, String userId);

    List<WorkbenchSession> findByUserId(String userId, int limit);

    List<WorkbenchSession> findByUserIdAndStatus(String userId, WorkbenchSessionStatus status);

    List<WorkbenchSession> findExpiredSessions(Instant now, int limit);

    WorkbenchSession save(WorkbenchSession session);
}
