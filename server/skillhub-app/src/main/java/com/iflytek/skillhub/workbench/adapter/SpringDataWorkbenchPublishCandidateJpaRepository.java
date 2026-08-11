package com.iflytek.skillhub.workbench.adapter;

import com.iflytek.skillhub.workbench.domain.WorkbenchPublishCandidate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataWorkbenchPublishCandidateJpaRepository extends JpaRepository<WorkbenchPublishCandidate, Long> {

    Optional<WorkbenchPublishCandidate> findTopBySessionIdOrderByCreatedAtDescIdDesc(Long sessionId);

    List<WorkbenchPublishCandidate> findBySessionIdOrderByCreatedAtDescIdDesc(Long sessionId);
}
