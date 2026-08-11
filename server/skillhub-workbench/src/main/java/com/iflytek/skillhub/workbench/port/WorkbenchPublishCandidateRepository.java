package com.iflytek.skillhub.workbench.port;

import com.iflytek.skillhub.workbench.domain.WorkbenchPublishCandidate;
import java.util.List;
import java.util.Optional;

public interface WorkbenchPublishCandidateRepository {
    WorkbenchPublishCandidate save(WorkbenchPublishCandidate candidate);

    Optional<WorkbenchPublishCandidate> findByIdAndSessionId(Long id, Long sessionId);

    Optional<WorkbenchPublishCandidate> findLatestBySessionId(Long sessionId);

    List<WorkbenchPublishCandidate> findBySessionId(Long sessionId);
}
