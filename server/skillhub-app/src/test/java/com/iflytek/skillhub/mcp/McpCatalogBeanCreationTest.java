package com.iflytek.skillhub.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class McpCatalogBeanCreationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class))
            .withBean(ObjectMapper.class)
            .withBean(ContextForgeMcpProperties.class)
            .withBean(ContextForgeMcpCatalogClient.class)
            .withBean(McpCatalogService.class);

    @Test
    void createsCatalogBeansWithConstructorInjection() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ContextForgeMcpCatalogClient.class);
            assertThat(context).hasSingleBean(McpCatalogService.class);
        });
    }
}
