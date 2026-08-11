package com.iflytek.skillhub.workbench.port;

import com.iflytek.skillhub.workbench.domain.WorkbenchSessionEvent;
import java.util.List;
import java.util.Optional;

public interface WorkbenchSessionEventRepository {
    WorkbenchSessionEvent append(WorkbenchSessionEvent event);

    Optional<WorkbenchSessionEvent> findBySessionIdAndEventId(Long sessionId, Long eventId);

    List<WorkbenchSessionEvent> findBySessionId(Long sessionId, int limit);

    List<WorkbenchSessionEvent> findBySessionIdAfterEventId(Long sessionId, Long afterEventId, int limit);
}
