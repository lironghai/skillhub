package com.iflytek.skillhub.workbench.poc;

import static org.assertj.core.api.Assertions.assertThat;

import io.agentscope.core.agent.RuntimeContext;
import org.junit.jupiter.api.Test;

class SkillHubRuntimeContextAdapterTest {

    @Test
    void mapsSkillHubUserAndWorkbenchSessionToAgentScopeRuntimeContext() {
        SkillHubRuntimeContextAdapter adapter = new SkillHubRuntimeContextAdapter();

        RuntimeContext context = adapter.createContext("user-123", "workbench-session-456");

        assertThat(context.getUserId()).isEqualTo("user-123");
        assertThat(context.getSessionId()).isEqualTo("workbench-session-456");
    }

    @Test
    void preservesSkillHubMappingBesideAgentScopeContext() {
        SkillHubRuntimeContextAdapter adapter = new SkillHubRuntimeContextAdapter();

        SkillHubRuntimeMapping mapping = adapter.map("user-a", "session-b");

        assertThat(mapping.userId()).isEqualTo("user-a");
        assertThat(mapping.workbenchSessionId()).isEqualTo("session-b");
        assertThat(mapping.runtimeContext().getUserId()).isEqualTo("user-a");
        assertThat(mapping.runtimeContext().getSessionId()).isEqualTo("session-b");
    }
}
