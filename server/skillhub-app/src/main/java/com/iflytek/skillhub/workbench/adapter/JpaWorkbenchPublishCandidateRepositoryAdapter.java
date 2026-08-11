package com.iflytek.skillhub.workbench.adapter;

import com.iflytek.skillhub.workbench.domain.WorkbenchPublishCandidate;
import com.iflytek.skillhub.workbench.port.WorkbenchPublishCandidateRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class JpaWorkbenchPublishCandidateRepositoryAdapter implements WorkbenchPublishCandidateRepository {

    private final SpringDataWorkbenchPublishCandidateJpaRepository repository;

    JpaWorkbenchPublishCandidateRepositoryAdapter(SpringDataWorkbenchPublishCandidateJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public WorkbenchPublishCandidate save(WorkbenchPublishCandidate candidate) {
        return repository.save(candidate);
    }

    @Override
    public Optional<WorkbenchPublishCandidate> findByIdAndSessionId(Long id, Long sessionId) {
        return repository.findById(id)
                .filter(candidate -> candidate.getSessionId().equals(sessionId));
    }

    @Override
    public Optional<WorkbenchPublishCandidate> findLatestBySessionId(Long sessionId) {
        return repository.findTopBySessionIdOrderByCreatedAtDescIdDesc(sessionId);
    }

    @Override
    public List<WorkbenchPublishCandidate> findBySessionId(Long sessionId) {
        return repository.findBySessionIdOrderByCreatedAtDescIdDesc(sessionId);
    }
}
