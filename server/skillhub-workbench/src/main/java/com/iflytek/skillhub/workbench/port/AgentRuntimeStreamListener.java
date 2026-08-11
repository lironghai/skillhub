package com.iflytek.skillhub.workbench.port;

public interface AgentRuntimeStreamListener {
    default void onRunStarted(String runId) {
    }

    default void onDelta(String phase, String content) {
    }

    default void onRuntimeEvent(AgentRuntimeEvent event) {
    }
}
