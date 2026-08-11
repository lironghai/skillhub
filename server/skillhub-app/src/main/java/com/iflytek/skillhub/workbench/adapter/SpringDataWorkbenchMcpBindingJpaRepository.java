package com.iflytek.skillhub.workbench.adapter;

import com.iflytek.skillhub.workbench.domain.WorkbenchMcpBinding;
import com.iflytek.skillhub.workbench.domain.WorkbenchMcpBindingStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataWorkbenchMcpBindingJpaRepository extends JpaRepository<WorkbenchMcpBinding, Long> {
    Optional<WorkbenchMcpBinding> findByIdAndSessionId(Long id, Long sessionId);

    List<WorkbenchMcpBinding> findBySessionIdOrderByCreatedAtAsc(Long sessionId);

    List<WorkbenchMcpBinding> findBySessionIdAndStatusOrderByCreatedAtAsc(
            Long sessionId,
            WorkbenchMcpBindingStatus status);
}
