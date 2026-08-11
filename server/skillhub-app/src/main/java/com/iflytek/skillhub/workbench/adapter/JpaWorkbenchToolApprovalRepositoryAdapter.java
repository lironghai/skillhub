package com.iflytek.skillhub.workbench.adapter;

import com.iflytek.skillhub.workbench.domain.WorkbenchToolApproval;
import com.iflytek.skillhub.workbench.domain.WorkbenchToolApprovalStatus;
import com.iflytek.skillhub.workbench.port.WorkbenchToolApprovalRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class JpaWorkbenchToolApprovalRepositoryAdapter implements WorkbenchToolApprovalRepository {

    private final SpringDataWorkbenchToolApprovalJpaRepository jpaRepository;

    public JpaWorkbenchToolApprovalRepositoryAdapter(SpringDataWorkbenchToolApprovalJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public WorkbenchToolApproval save(WorkbenchToolApproval approval) {
        return jpaRepository.save(approval);
    }

    @Override
    public Optional<WorkbenchToolApproval> findByIdAndSessionId(Long id, Long sessionId) {
        return jpaRepository.findByIdAndSessionId(id, sessionId);
    }

    @Override
    public Optional<WorkbenchToolApproval> findByEventId(Long eventId) {
        return jpaRepository.findByEventId(eventId);
    }

    @Override
    public List<WorkbenchToolApproval> findPendingBySessionId(Long sessionId) {
        return jpaRepository.findBySessionIdAndStatusOrderByCreatedAtAsc(
                sessionId,
                WorkbenchToolApprovalStatus.PENDING);
    }
}
