package com.iflytek.skillhub.workbench.adapter;

import com.iflytek.skillhub.workbench.domain.WorkbenchFileSnapshot;
import com.iflytek.skillhub.workbench.domain.WorkbenchFileSnapshotType;
import com.iflytek.skillhub.workbench.port.WorkbenchFileSnapshotRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class JpaWorkbenchFileSnapshotRepositoryAdapter implements WorkbenchFileSnapshotRepository {

    private final SpringDataWorkbenchFileSnapshotJpaRepository jpaRepository;

    public JpaWorkbenchFileSnapshotRepositoryAdapter(SpringDataWorkbenchFileSnapshotJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public WorkbenchFileSnapshot save(WorkbenchFileSnapshot snapshot) {
        return jpaRepository.save(snapshot);
    }

    @Override
    public Optional<WorkbenchFileSnapshot> findBySessionIdAndTypeAndFilePath(
            Long sessionId,
            WorkbenchFileSnapshotType snapshotType,
            String filePath) {
        return jpaRepository.findBySessionIdAndSnapshotTypeAndFilePath(sessionId, snapshotType, filePath);
    }

    @Override
    public List<WorkbenchFileSnapshot> findBySessionIdAndType(
            Long sessionId,
            WorkbenchFileSnapshotType snapshotType) {
        return jpaRepository.findBySessionIdAndSnapshotType(sessionId, snapshotType);
    }

    @Override
    public void deleteBySessionIdAndTypeAndFilePath(
            Long sessionId,
            WorkbenchFileSnapshotType snapshotType,
            String filePath) {
        jpaRepository.deleteBySessionIdAndSnapshotTypeAndFilePath(sessionId, snapshotType, filePath);
    }
}
