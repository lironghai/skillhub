package com.iflytek.skillhub.workbench.adapter;

import com.iflytek.skillhub.workbench.domain.WorkbenchSessionEvent;
import com.iflytek.skillhub.workbench.port.WorkbenchSessionEventRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
public class JpaWorkbenchSessionEventRepositoryAdapter implements WorkbenchSessionEventRepository {

    private final SpringDataWorkbenchSessionEventJpaRepository jpaRepository;

    public JpaWorkbenchSessionEventRepositoryAdapter(SpringDataWorkbenchSessionEventJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public WorkbenchSessionEvent append(WorkbenchSessionEvent event) {
        return jpaRepository.save(event);
    }

    @Override
    public Optional<WorkbenchSessionEvent> findBySessionIdAndEventId(Long sessionId, Long eventId) {
        return jpaRepository.findBySessionIdAndId(sessionId, eventId);
    }

    @Override
    public List<WorkbenchSessionEvent> findBySessionId(Long sessionId, int limit) {
        List<WorkbenchSessionEvent> latest = new ArrayList<>(jpaRepository.findBySessionIdOrderByIdDesc(
                sessionId,
                PageRequest.of(0, Math.max(1, limit))));
        Collections.reverse(latest);
        return latest;
    }

    @Override
    public List<WorkbenchSessionEvent> findBySessionIdAfterEventId(Long sessionId, Long afterEventId, int limit) {
        return jpaRepository.findBySessionIdAndIdGreaterThanOrderByIdAsc(
                sessionId,
                afterEventId,
                PageRequest.of(0, Math.max(1, limit)));
    }
}
