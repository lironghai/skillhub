package com.iflytek.skillhub.workbench.poc;

import static org.assertj.core.api.Assertions.assertThat;

import io.agentscope.harness.agent.tools.McpServerConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentScopeHarnessDependencyTest {

    @Test
    void compilesAgainstHarnessMcpConfigurationClass() {
        McpServerConfig config = new McpServerConfig();

        config.setTransport("stdio");
        config.setEnableTools(List.of("list_skills"));

        assertThat(config.getTransport()).isEqualTo("stdio");
        assertThat(config.getEnableTools()).containsExactly("list_skills");
    }
}
