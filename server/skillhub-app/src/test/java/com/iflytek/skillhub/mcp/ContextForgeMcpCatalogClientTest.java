package com.iflytek.skillhub.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ContextForgeMcpCatalogClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void fetchServersLogsInAndRequestsCatalogWithBearerTokenAndFilters() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ContextForgeMcpProperties properties = new ContextForgeMcpProperties();
        properties.setBaseUrl("https://contextforge.example");
        properties.setUsername("admin@example.com");
        properties.setPassword("secret");
        ContextForgeMcpCatalogClient client = new ContextForgeMcpCatalogClient(builder.build(), objectMapper, properties);

        server.expect(once(), requestTo("https://contextforge.example/auth/email/login"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "access_token": "ctx-token",
                          "token_type": "bearer",
                          "expires_in": 3600,
                          "user": {"email": "admin@example.com"}
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://contextforge.example/admin/mcp-registry/servers?show_available_only=false&limit=24&offset=48&search=git&category=Software%20Development&auth_type=OAuth2.1&provider=GitHub&tags=development&tags=git"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer ctx-token"))
                .andRespond(withSuccess("""
                        {
                          "servers": [
                            {
                              "id": "github",
                              "name": "GitHub",
                              "category": "Software Development",
                              "url": "https://api.githubcopilot.com/mcp",
                              "auth_type": "OAuth2.1",
                              "provider": "GitHub",
                              "description": "Version control and collaborative software development",
                              "requires_api_key": false,
                              "secure": true,
                              "tags": ["development", "git"],
                              "transport": "STREAMABLEHTTP",
                              "logo_url": "https://example.com/github.svg",
                              "documentation_url": "https://docs.github.com",
                              "is_registered": true,
                              "is_available": true,
                              "requires_oauth_config": false
                            }
                          ],
                          "total": 1,
                          "categories": ["Software Development"],
                          "auth_types": ["OAuth2.1"],
                          "providers": ["GitHub"],
                          "all_tags": ["development", "git"]
                        }
                        """, MediaType.APPLICATION_JSON));

        McpCatalogResponse response = client.fetchServers(new McpCatalogQuery(
                "git",
                "Software Development",
                "OAuth2.1",
                "GitHub",
                java.util.List.of("development", "git"),
                2,
                24
        ));

        assertThat(response.total()).isEqualTo(1);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().id()).isEqualTo("github");
        assertThat(response.items().getFirst().name()).isEqualTo("GitHub");
        assertThat(response.items().getFirst().url()).isEqualTo("https://api.githubcopilot.com/mcp");
        assertThat(response.items().getFirst().tags()).containsExactly("development", "git");
        assertThat(response.categories()).containsExactly("Software Development");
        server.verify();
    }

    @Test
    void fetchServersReusesUnexpiredContextForgeTokenButDoesNotCacheCatalogResponses() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ContextForgeMcpProperties properties = new ContextForgeMcpProperties();
        properties.setBaseUrl("https://contextforge.example");
        properties.setUsername("admin@example.com");
        properties.setPassword("secret");
        ContextForgeMcpCatalogClient client = new ContextForgeMcpCatalogClient(builder.build(), objectMapper, properties);

        server.expect(once(), requestTo("https://contextforge.example/auth/email/login"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "access_token": "ctx-token",
                          "token_type": "bearer",
                          "expires_in": 3600
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(manyTimes(), requestTo("https://contextforge.example/admin/mcp-registry/servers?show_available_only=false&limit=24&offset=0"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer ctx-token"))
                .andRespond(withSuccess("""
                        {
                          "servers": [],
                          "total": 0,
                          "categories": [],
                          "auth_types": [],
                          "providers": [],
                          "all_tags": []
                        }
                        """, MediaType.APPLICATION_JSON));

        McpCatalogQuery query = new McpCatalogQuery(null, null, null, null, List.of(), 0, 24);

        client.fetchServers(query);
        client.fetchServers(query);

        server.verify();
    }

    @Test
    void fetchInternalServersBuildsClientConnectionUrlsFromVirtualServerIds() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ContextForgeMcpProperties properties = new ContextForgeMcpProperties();
        properties.setBaseUrl("https://contextforge.internal");
        properties.setPublicBaseUrl("https://skillhub.example/contextforge/");
        properties.setUsername("admin@example.com");
        properties.setPassword("secret");
        ContextForgeMcpCatalogClient client = new ContextForgeMcpCatalogClient(builder.build(), objectMapper, properties);

        server.expect(once(), requestTo("https://contextforge.internal/auth/email/login"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "access_token": "ctx-token",
                          "token_type": "bearer",
                          "expires_in": 3600
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://contextforge.internal/admin/servers?include_inactive=true&page=1&per_page=24"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer ctx-token"))
                .andRespond(withSuccess("""
                        {
                          "data": [
                            {
                              "id": "34eaa0d257da49608da2c6b079ed0b5",
                              "name": "bdc4_group",
                              "description": "bdc4_group",
                              "icon": "https://static.example.com/icons/bdc4.png",
                              "enabled": true,
                              "associatedTools": ["query_report_by_code", "legacy-tool-b"],
                              "associatedToolIds": ["tool-a", "legacy-tool-b"],
                              "associatedResources": ["res-a"],
                              "associatedPrompts": ["prompt-a", "prompt-b", "prompt-c"],
                              "tags": [{"name": "bdc4"}],
                              "ownerEmail": "admin@mcp-context-forge.yingxiong.com",
                              "team": "Platform Admin",
                              "visibility": "public"
                            }
                          ],
                          "pagination": {
                            "page": 1,
                            "per_page": 24,
                            "total_items": 1,
                            "total_pages": 1
                          },
                          "links": null
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://contextforge.internal/admin/tools?include_inactive=true&page=1&per_page=100"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer ctx-token"))
                .andRespond(withSuccess("""
                        {
                          "data": [
                            {
                              "id": "tool-a",
                              "name": "query_report_by_code",
                              "displayName": "Query report by code",
                              "description": "Query BDC report data by code",
                              "inputSchema": {
                                "type": "object",
                                "properties": {
                                  "reportCode": {"type": "string"}
                                },
                                "required": ["reportCode"]
                              },
                              "outputSchema": {
                                "type": "object",
                                "properties": {
                                  "rows": {"type": "array"}
                                }
                              }
                            }
                          ],
                          "pagination": {"page": 1, "per_page": 100, "total_items": 1, "total_pages": 1},
                          "links": null
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://contextforge.internal/admin/resources?include_inactive=true&page=1&per_page=100"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer ctx-token"))
                .andRespond(withSuccess("""
                        {
                          "data": [
                            {
                              "id": "res-a",
                              "name": "BDC schema",
                              "description": "Report schema resource"
                            }
                          ],
                          "pagination": {"page": 1, "per_page": 100, "total_items": 1, "total_pages": 1},
                          "links": null
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://contextforge.internal/admin/prompts?include_inactive=true&page=1&per_page=100"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer ctx-token"))
                .andRespond(withSuccess("""
                        {
                          "data": [
                            {
                              "id": "prompt-a",
                              "name": "BDC prompt",
                              "description": "Assistant prompt"
                            }
                          ],
                          "pagination": {"page": 1, "per_page": 100, "total_items": 1, "total_pages": 1},
                          "links": null
                        }
                        """, MediaType.APPLICATION_JSON));

        McpInternalServerResponse response = client.fetchInternalServers(new McpCatalogQuery(
                null,
                null,
                null,
                null,
                List.of(),
                0,
                24
        ));

        assertThat(response.total()).isEqualTo(1);
        assertThat(response.items()).hasSize(1);
        McpInternalServerItemResponse item = response.items().getFirst();
        assertThat(item.id()).isEqualTo("34eaa0d257da49608da2c6b079ed0b5");
        assertThat(item.name()).isEqualTo("bdc4_group");
        assertThat(item.iconUrl()).isEqualTo("https://static.example.com/icons/bdc4.png");
        assertThat(item.streamableHttpUrl()).isEqualTo("https://skillhub.example/contextforge/servers/34eaa0d257da49608da2c6b079ed0b5/mcp");
        assertThat(item.sseUrl()).isEqualTo("https://skillhub.example/contextforge/servers/34eaa0d257da49608da2c6b079ed0b5/sse");
        assertThat(item.toolCount()).isEqualTo(2);
        assertThat(item.resourceCount()).isEqualTo(1);
        assertThat(item.promptCount()).isEqualTo(3);
        assertThat(item.tools())
                .extracting(McpAssociatedItemResponse::name)
                .containsExactly("query_report_by_code", "legacy-tool-b");
        assertThat(item.tools().getFirst().description()).isEqualTo("Query BDC report data by code");
        assertThat(item.tools().getFirst().inputSchema().path("properties").path("reportCode").path("type").asText()).isEqualTo("string");
        assertThat(item.tools().getFirst().outputSchema().path("properties").path("rows").path("type").asText()).isEqualTo("array");
        assertThat(item.resources())
                .extracting(McpAssociatedItemResponse::name)
                .containsExactly("BDC schema");
        assertThat(item.prompts())
                .extracting(McpAssociatedItemResponse::name)
                .containsExactly("BDC prompt", "prompt-b", "prompt-c");
        assertThat(item.tags()).containsExactly("bdc4");
        server.verify();
    }
}
