package com.iflytek.skillhub.mcp;

import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpCatalogControllerTest {

    @Test
    void serversDelegatesReadOnlyCatalogSearchAndWrapsResponse() {
        McpCatalogService service = mock(McpCatalogService.class);
        McpCatalogResponse catalog = new McpCatalogResponse(
                List.of(new McpCatalogItemResponse(
                        "github",
                        "GitHub",
                        "Software Development",
                        "GitHub",
                        "Version control",
                        "https://api.githubcopilot.com/mcp",
                        "OAuth2.1",
                        false,
                        true,
                        List.of("git"),
                        "STREAMABLEHTTP",
                        null,
                        "https://docs.github.com",
                        true,
                        true,
                        false
                )),
                1,
                2,
                24,
                List.of("Software Development"),
                List.of("OAuth2.1"),
                List.of("GitHub"),
                List.of("git")
        );
        when(service.search(
                eq("git"),
                eq("Software Development"),
                eq("OAuth2.1"),
                eq("GitHub"),
                eq(List.of("git")),
                eq("2"),
                eq("24")
        )).thenReturn(catalog);

        McpCatalogController controller = new McpCatalogController(service, responseFactory());

        ApiResponse<McpCatalogResponse> response = controller.servers(
                "git",
                "Software Development",
                "OAuth2.1",
                "GitHub",
                List.of("git"),
                "2",
                "24"
        );

        assertThat(response.code()).isZero();
        assertThat(response.data()).isSameAs(catalog);
        assertThat(response.data().items()).hasSize(1);
        assertThat(response.data().items().getFirst().name()).isEqualTo("GitHub");
        verify(service).search(
                "git",
                "Software Development",
                "OAuth2.1",
                "GitHub",
                List.of("git"),
                "2",
                "24"
        );
    }

    @Test
    void internalServersDelegatesReadOnlyVirtualServerSearchAndWrapsResponse() {
        McpCatalogService service = mock(McpCatalogService.class);
        McpInternalServerResponse internalServers = new McpInternalServerResponse(
                List.of(new McpInternalServerItemResponse(
                        "34eaa0d257da49608da2c6b079ed0b5",
                        "bdc4_group",
                        "bdc4_group",
                        true,
                        "public",
                        "admin@mcp-context-forge.yingxiong.com",
                        "Platform Admin",
                        5,
                        11,
                        3,
                        List.of(new McpAssociatedItemResponse(
                                "tool-a",
                                "query_report_by_code",
                                "Query BDC report data by code"
                        )),
                        List.of(new McpAssociatedItemResponse(
                                "res-a",
                                "BDC schema",
                                "Report schema resource"
                        )),
                        List.of(new McpAssociatedItemResponse(
                                "prompt-a",
                                "BDC prompt",
                                "Assistant prompt"
                        )),
                        List.of("bdc4"),
                        "http://skillhub.example/contextforge/servers/34eaa0d257da49608da2c6b079ed0b5/mcp",
                        "http://skillhub.example/contextforge/servers/34eaa0d257da49608da2c6b079ed0b5/sse"
                )),
                1,
                0,
                24
        );
        when(service.internalServers(eq("bdc"), eq("0"), eq("24"))).thenReturn(internalServers);

        McpCatalogController controller = new McpCatalogController(service, responseFactory());

        ApiResponse<McpInternalServerResponse> response = controller.internalServers("bdc", "0", "24");

        assertThat(response.code()).isZero();
        assertThat(response.data()).isSameAs(internalServers);
        assertThat(response.data().items().getFirst().streamableHttpUrl()).contains("/servers/34eaa0d257da49608da2c6b079ed0b5/mcp");
        verify(service).internalServers("bdc", "0", "24");
    }

    private ApiResponseFactory responseFactory() {
        StaticMessageSource messageSource = new StaticMessageSource();
        messageSource.addMessage("response.success.read", java.util.Locale.ENGLISH, "Fetched successfully");
        return new ApiResponseFactory(
                messageSource,
                Clock.fixed(Instant.parse("2026-06-09T08:00:00Z"), ZoneOffset.UTC)
        );
    }
}
