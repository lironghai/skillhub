package com.iflytek.skillhub.workbench.adapter;

import com.iflytek.skillhub.workbench.domain.WorkbenchFileSnapshot;
import com.iflytek.skillhub.workbench.domain.WorkbenchFileSnapshotType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataWorkbenchFileSnapshotJpaRepository extends JpaRepository<WorkbenchFileSnapshot, Long> {
    Optional<WorkbenchFileSnapshot> findBySessionIdAndSnapshotTypeAndFilePath(
            Long sessionId,
            WorkbenchFileSnapshotType snapshotType,
            String filePath);

    List<WorkbenchFileSnapshot> findBySessionIdAndSnapshotType(Long sessionId, WorkbenchFileSnapshotType snapshotType);

    void deleteBySessionIdAndSnapshotTypeAndFilePath(
            Long sessionId,
            WorkbenchFileSnapshotType snapshotType,
            String filePath);
}
