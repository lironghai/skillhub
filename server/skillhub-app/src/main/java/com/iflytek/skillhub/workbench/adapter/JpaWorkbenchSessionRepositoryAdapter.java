package com.iflytek.skillhub.workbench.adapter;

import com.iflytek.skillhub.workbench.domain.WorkbenchSession;
import com.iflytek.skillhub.workbench.domain.WorkbenchSessionStatus;
import com.iflytek.skillhub.workbench.port.WorkbenchSessionRepository;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
public class JpaWorkbenchSessionRepositoryAdapter implements WorkbenchSessionRepository {

    private static final EnumSet<WorkbenchSessionStatus> TERMINAL_STATUSES = EnumSet.of(
            WorkbenchSessionStatus.PUBLISHED,
            WorkbenchSessionStatus.FAILED,
            WorkbenchSessionStatus.CANCELLED,
            WorkbenchSessionStatus.EXPIRED);

    private final SpringDataWorkbenchSessionJpaRepository jpaRepository;

    public JpaWorkbenchSessionRepositoryAdapter(SpringDataWorkbenchSessionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<WorkbenchSession> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<WorkbenchSession> findByIdAndUserId(Long id, String userId) {
        return jpaRepository.findById(id)
                .filter(session -> session.getUserId().equals(userId));
    }

    @Override
    public List<WorkbenchSession> findByUserId(String userId, int limit) {
        return jpaRepository.findByUserIdOrderByUpdatedAtDesc(userId, PageRequest.of(0, Math.max(1, limit)));
    }

    @Override
    public List<WorkbenchSession> findByUserIdAndStatus(String userId, WorkbenchSessionStatus status) {
        return jpaRepository.findByUserIdAndStatusOrderByUpdatedAtDesc(userId, status);
    }

    @Override
    public List<WorkbenchSession> findExpiredSessions(Instant now, int limit) {
        return jpaRepository.findByExpiresAtBeforeAndStatusNotInOrderByExpiresAtAsc(
                now,
                TERMINAL_STATUSES,
                PageRequest.of(0, Math.max(1, limit)));
    }

    @Override
    public WorkbenchSession save(WorkbenchSession session) {
        return jpaRepository.save(session);
    }
}
