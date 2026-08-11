package com.iflytek.skillhub.workbench.poc;

import io.agentscope.core.agent.RuntimeContext;

public record SkillHubRuntimeMapping(
        String userId,
        String workbenchSessionId,
        RuntimeContext runtimeContext) {}
