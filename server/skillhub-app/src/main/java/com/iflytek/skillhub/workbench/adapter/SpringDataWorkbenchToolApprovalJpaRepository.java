package com.iflytek.skillhub.workbench.adapter;

import com.iflytek.skillhub.workbench.domain.WorkbenchToolApproval;
import com.iflytek.skillhub.workbench.domain.WorkbenchToolApprovalStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataWorkbenchToolApprovalJpaRepository extends JpaRepository<WorkbenchToolApproval, Long> {
    Optional<WorkbenchToolApproval> findByIdAndSessionId(Long id, Long sessionId);

    Optional<WorkbenchToolApproval> findByEventId(Long eventId);

    List<WorkbenchToolApproval> findBySessionIdAndStatusOrderByCreatedAtAsc(
            Long sessionId,
            WorkbenchToolApprovalStatus status);
}
