package com.iflytek.skillhub.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

class McpCatalogBeanCreationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class))
            .withBean(ObjectMapper.class)
            .withUserConfiguration(CatalogConfiguration.class);

    @Test
    void createsCatalogBeansWithConstructorInjection() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ContextForgeMcpCatalogClient.class);
            assertThat(context).hasSingleBean(McpCatalogService.class);
        });
    }

    @Test
    void disablesCatalogBeansWhenTheMcpMasterSwitchIsOff() {
        contextRunner
                .withPropertyValues(
                        "skillhub.mcp.enabled=false",
                        "skillhub.mcp.context-forge.enabled=true"
                )
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ContextForgeMcpCatalogClient.class);
                    assertThat(context).doesNotHaveBean(McpCatalogService.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({ContextForgeMcpProperties.class, ContextForgeMcpCatalogClient.class, McpCatalogService.class})
    static class CatalogConfiguration {
    }
}
