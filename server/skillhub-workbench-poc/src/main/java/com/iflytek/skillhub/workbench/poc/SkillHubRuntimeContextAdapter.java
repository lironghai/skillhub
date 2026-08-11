package com.iflytek.skillhub.workbench.poc;

import io.agentscope.core.agent.RuntimeContext;
import java.util.Objects;

public final class SkillHubRuntimeContextAdapter {

    public RuntimeContext createContext(String userId, String workbenchSessionId) {
        requireText(userId, "userId");
        requireText(workbenchSessionId, "workbenchSessionId");
        return RuntimeContext.builder()
                .userId(userId)
                .sessionId(workbenchSessionId)
                .build();
    }

    public SkillHubRuntimeMapping map(String userId, String workbenchSessionId) {
        return new SkillHubRuntimeMapping(userId, workbenchSessionId, createContext(userId, workbenchSessionId));
    }

    private static void requireText(String value, String name) {
        if (Objects.requireNonNull(value, name).isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
