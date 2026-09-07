package com.iflytek.skillhub.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "skillhub.mcp", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SkillHubMcpConfiguration {

    @Bean
    public ToolCallbackProvider skillHubMcpToolCallbackProvider(SkillHubMcpTools tools) {
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }
}
