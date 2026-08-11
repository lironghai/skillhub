package com.iflytek.skillhub.workbench.port;

import com.iflytek.skillhub.workbench.domain.WorkbenchFileSnapshot;
import com.iflytek.skillhub.workbench.domain.WorkbenchFileSnapshotType;
import java.util.List;
import java.util.Optional;

public interface WorkbenchFileSnapshotRepository {
    WorkbenchFileSnapshot save(WorkbenchFileSnapshot snapshot);

    Optional<WorkbenchFileSnapshot> findBySessionIdAndTypeAndFilePath(
            Long sessionId, WorkbenchFileSnapshotType snapshotType, String filePath);

    List<WorkbenchFileSnapshot> findBySessionIdAndType(Long sessionId, WorkbenchFileSnapshotType snapshotType);

    void deleteBySessionIdAndTypeAndFilePath(Long sessionId, WorkbenchFileSnapshotType snapshotType, String filePath);
}
