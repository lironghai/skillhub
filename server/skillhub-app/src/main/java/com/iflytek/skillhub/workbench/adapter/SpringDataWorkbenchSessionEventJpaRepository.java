package com.iflytek.skillhub.workbench.adapter;

import com.iflytek.skillhub.workbench.domain.WorkbenchSessionEvent;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataWorkbenchSessionEventJpaRepository extends JpaRepository<WorkbenchSessionEvent, Long> {
    Optional<WorkbenchSessionEvent> findBySessionIdAndId(Long sessionId, Long id);

    List<WorkbenchSessionEvent> findBySessionIdOrderByIdAsc(Long sessionId, Pageable pageable);

    List<WorkbenchSessionEvent> findBySessionIdOrderByIdDesc(Long sessionId, Pageable pageable);

    List<WorkbenchSessionEvent> findBySessionIdAndIdGreaterThanOrderByIdAsc(
            Long sessionId,
            Long id,
            Pageable pageable);
}
