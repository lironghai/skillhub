package com.iflytek.skillhub.workbench.adapter;

import com.iflytek.skillhub.workbench.domain.WorkbenchSession;
import com.iflytek.skillhub.workbench.domain.WorkbenchSessionStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataWorkbenchSessionJpaRepository extends JpaRepository<WorkbenchSession, Long> {
    List<WorkbenchSession> findByUserIdOrderByUpdatedAtDesc(String userId, Pageable pageable);

    List<WorkbenchSession> findByUserIdAndStatusOrderByUpdatedAtDesc(String userId, WorkbenchSessionStatus status);

    List<WorkbenchSession> findByExpiresAtBeforeAndStatusNotInOrderByExpiresAtAsc(
            Instant now,
            Collection<WorkbenchSessionStatus> statuses,
            Pageable pageable);
}
