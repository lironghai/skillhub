package com.iflytek.skillhub.workbench.port;

import com.iflytek.skillhub.workbench.domain.WorkbenchSession;
import java.time.Instant;
import java.util.List;

public record AgentRuntimeRunHandle(
        String runId,
        AgentRuntimeRunStatus status,
        String message,
        List<AgentRuntimeEvent> events,
        Instant startedAt) {
    public AgentRuntimeRunHandle {
        WorkbenchSession.requireText(runId, "runId");
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        events = events == null ? List.of() : List.copyOf(events);
        startedAt = startedAt == null ? Instant.now() : startedAt;
    }

    public AgentRuntimeRunHandle(
            String runId,
            AgentRuntimeRunStatus status,
            String message,
            List<AgentRuntimeEvent> events) {
        this(runId, status, message, events, Instant.now());
    }

    public AgentRuntimeRunHandle(String runId, AgentRuntimeRunStatus status, String message) {
        this(runId, status, message, List.of(), Instant.now());
    }
}
