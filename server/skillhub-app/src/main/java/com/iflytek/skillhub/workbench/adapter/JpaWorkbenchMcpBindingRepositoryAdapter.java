package com.iflytek.skillhub.workbench.adapter;

import com.iflytek.skillhub.workbench.domain.WorkbenchMcpBinding;
import com.iflytek.skillhub.workbench.domain.WorkbenchMcpBindingStatus;
import com.iflytek.skillhub.workbench.port.WorkbenchMcpBindingRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class JpaWorkbenchMcpBindingRepositoryAdapter implements WorkbenchMcpBindingRepository {

    private final SpringDataWorkbenchMcpBindingJpaRepository jpaRepository;

    public JpaWorkbenchMcpBindingRepositoryAdapter(SpringDataWorkbenchMcpBindingJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public WorkbenchMcpBinding save(WorkbenchMcpBinding binding) {
        return jpaRepository.save(binding);
    }

    @Override
    public Optional<WorkbenchMcpBinding> findByIdAndSessionId(Long id, Long sessionId) {
        return jpaRepository.findByIdAndSessionId(id, sessionId);
    }

    @Override
    public List<WorkbenchMcpBinding> findBySessionId(Long sessionId) {
        return jpaRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    @Override
    public List<WorkbenchMcpBinding> findBySessionIdAndStatus(Long sessionId, WorkbenchMcpBindingStatus status) {
        return jpaRepository.findBySessionIdAndStatusOrderByCreatedAtAsc(sessionId, status);
    }
}
