package com.iflytek.skillhub.workbench.port;

import java.util.Optional;

public interface AgentRuntimePort {
    AgentRuntimeRunHandle start(AgentRuntimeRunCommand command);

    default AgentRuntimeRunHandle startStreaming(AgentRuntimeRunCommand command, AgentRuntimeStreamListener listener) {
        AgentRuntimeRunHandle handle = start(command);
        if (listener != null) {
            listener.onRunStarted(handle.runId());
            for (AgentRuntimeEvent event : handle.events()) {
                listener.onRuntimeEvent(event);
            }
        }
        return handle;
    }

    AgentRuntimeRunHandle cancel(AgentRuntimeCancelCommand command);

    default Optional<AgentRuntimeRunHandle> findActiveRun(String userId, Long sessionId) {
        return Optional.empty();
    }
}
