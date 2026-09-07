package com.iflytek.skillhub.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ContextForgeMcpCatalogClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void contextForgeTimeoutsAreFiniteByDefault() {
        ContextForgeMcpProperties properties = new ContextForgeMcpProperties();

        assertThat(properties.getConnectTimeout()).isEqualTo(java.time.Duration.ofSeconds(5));
        assertThat(properties.getReadTimeout()).isEqualTo(java.time.Duration.ofSeconds(15));
    }

    @Test
    void fetchServersRejectsNullCatalogResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ContextForgeMcpProperties properties = new ContextForgeMcpProperties();
        properties.setBaseUrl("https://contextforge.example");
        properties.setUsername("admin@example.com");
        properties.setPassword("secret");
        ContextForgeMcpCatalogClient client = new ContextForgeMcpCatalogClient(builder.build(), objectMapper, properties);

        server.expect(requestTo("https://contextforge.example/auth/email/login"))
                .andRespond(withSuccess("{\"access_token\":\"ctx-token\",\"expires_in\":3600}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://contextforge.example/admin/mcp-registry/servers?show_available_only=false&limit=24&offset=0"))
                .andRespond(withSuccess("null", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchServers(
                new McpCatalogQuery(null, null, null, null, List.of(), 0, 24)))
                .isInstanceOf(McpCatalogUnavailableException.class);
        server.verify();
    }

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
    void fetchServersKeepsLargePageOffsetPositive() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ContextForgeMcpProperties properties = new ContextForgeMcpProperties();
        properties.setBaseUrl("https://contextforge.example");
        properties.setUsername("admin@example.com");
        properties.setPassword("secret");
        ContextForgeMcpCatalogClient client = new ContextForgeMcpCatalogClient(builder.build(), objectMapper, properties);

        server.expect(once(), requestTo("https://contextforge.example/auth/email/login"))
                .andRespond(withSuccess("{\"access_token\":\"ctx-token\",\"expires_in\":3600}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://contextforge.example/admin/mcp-registry/servers?show_available_only=false&limit=100&offset=214748364700"))
                .andRespond(withSuccess("{\"servers\":[],\"total\":0}", MediaType.APPLICATION_JSON));

        client.fetchServers(new McpCatalogQuery(null, null, null, null, List.of(), Integer.MAX_VALUE, 100));

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
        server.expect(once(), requestTo("https://contextforge.internal/admin/servers?include_inactive=false&page=1&per_page=100"))
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
                              "ownerEmail": "admin",
                              "team": "Platform Admin",
                              "visibility": "public"
                            },
                            {
                              "id": "private-server",
                              "name": "private_server",
                              "description": "not visible to SkillHub users",
                              "enabled": true,
                              "associatedTools": [],
                              "associatedResources": [],
                              "associatedPrompts": [],
                              "tags": [],
                              "visibility": "private"
                            }
                          ],
                          "pagination": {
                            "page": 1,
                            "per_page": 100,
                            "total_items": 2,
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
                              "visibility": "public",
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
                          "pagination": {"page": 1, "per_page": 100, "total_items": 101, "total_pages": 2},
                          "links": null
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://contextforge.internal/admin/tools?include_inactive=true&page=2&per_page=100"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer ctx-token"))
                .andRespond(withSuccess("""
                        {
                          "data": [
                            {"id": "tool-z", "name": "another_tool", "visibility": "public"},
                            {"id": "legacy-tool-b", "name": "legacy-tool-b", "visibility": "public"}
                          ],
                          "pagination": {"page": 2, "per_page": 100, "total_items": 101, "total_pages": 2},
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
                              "description": "Report schema resource",
                              "visibility": "public"
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
                              "description": "Assistant prompt",
                              "visibility": "public"
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
        assertThat(item.promptCount()).isEqualTo(1);
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
                .containsExactly("BDC prompt");
        assertThat(item.tags()).containsExactly("bdc4");
        server.verify();
    }

    @Test
    void fetchInternalServersScopesToConfiguredTeamAndIncludesTeamVisibility() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ContextForgeMcpProperties properties = new ContextForgeMcpProperties();
        properties.setBaseUrl("https://contextforge.internal");
        properties.setPublicBaseUrl("https://skillhub.example/contextforge");
        properties.setUsername("admin@example.com");
        properties.setPassword("secret");
        properties.setTeamId("e69584b9-1829-4279-896f-69cfbccc3f73");
        ContextForgeMcpCatalogClient client = new ContextForgeMcpCatalogClient(builder.build(), objectMapper, properties);

        server.expect(once(), requestTo("https://contextforge.internal/auth/email/login"))
                .andRespond(withSuccess("{\"access_token\":\"ctx-token\",\"expires_in\":3600}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://contextforge.internal/admin/servers?include_inactive=false&page=1&per_page=100&team_id=e69584b918294279896f69cfbccc3f73"))
                .andExpect(header("Authorization", "Bearer ctx-token"))
                .andRespond(withSuccess("""
                        {
                          "data": [
                            {"id":"team-public","name":"team-public","enabled":true,"visibility":"public","team_id":"e69584b918294279896f69cfbccc3f73","associatedTools":["allowed-tool","private-tool"],"associatedResources":[],"associatedPrompts":[]},
                            {"id":"team-private","name":"team-private","enabled":true,"visibility":"team","team_id":"e69584b918294279896f69cfbccc3f73","associatedTools":[],"associatedResources":[],"associatedPrompts":[]},
                            {"id":"other","name":"other","enabled":true,"visibility":"public","team_id":"other-team","associatedTools":[],"associatedResources":[],"associatedPrompts":[]}
                          ],
                          "pagination": {"page":1,"per_page":100,"total_items":3,"total_pages":1}
                        }
                        """, MediaType.APPLICATION_JSON));

        server.expect(once(), requestTo("https://contextforge.internal/admin/tools?include_inactive=true&page=1&per_page=100"))
                .andExpect(header("Authorization", "Bearer ctx-token"))
                .andRespond(withSuccess("""
                        {
                          "data": [
                            {"id":"allowed-tool","name":"allowed-tool","team_id":"e69584b9-1829-4279-896f-69cfbccc3f73","visibility":"team","description":"allowed"},
                            {"id":"private-tool","name":"private-tool","team_id":"other-team","visibility":"private","description":"must not leak"}
                          ],
                          "pagination": {"total_items":2}
                        }
                        """, MediaType.APPLICATION_JSON));

        McpInternalServerResponse response = client.fetchInternalServers(
                new McpCatalogQuery(null, null, null, null, List.of(), 0, 24));

        assertThat(response.items()).extracting(McpInternalServerItemResponse::id)
                .containsExactly("team-public", "team-private");
        assertThat(response.items()).extracting(McpInternalServerItemResponse::visibility)
                .containsExactly("public", "team");
        assertThat(response.items().getFirst().tools()).extracting(McpAssociatedItemResponse::name)
                .containsExactly("allowed-tool");
        server.verify();
    }

    @Test
    void fetchInternalServersNeverReplacesDeniedToolResourceOrPromptIdsWithSameNamedItems() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ContextForgeMcpProperties properties = new ContextForgeMcpProperties();
        properties.setBaseUrl("https://contextforge.internal");
        properties.setUsername("admin@example.com");
        properties.setPassword("secret");
        ContextForgeMcpCatalogClient client = new ContextForgeMcpCatalogClient(builder.build(), objectMapper, properties);

        server.expect(once(), requestTo("https://contextforge.internal/auth/email/login"))
                .andRespond(withSuccess("{\"access_token\":\"ctx-token\",\"expires_in\":3600}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://contextforge.internal/admin/servers?include_inactive=false&page=1&per_page=100"))
                .andRespond(withSuccess("""
                        {"data":[{"id":"public-server","name":"public-server","enabled":true,"visibility":"public","associatedTools":["shared-name"],"associatedToolIds":["private-tool"],"associatedResources":["private-resource"],"associatedPrompts":["private-prompt"]}],"pagination":{"total_items":1}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://contextforge.internal/admin/tools?include_inactive=true&page=1&per_page=100"))
                .andRespond(withSuccess("""
                        {"data":[{"id":"private-tool","name":"shared-name","visibility":"private","team_id":"other-team"},{"id":"allowed-tool","name":"shared-name","visibility":"public"},{"id":"another-allowed-tool","name":"private-tool","visibility":"public"}],"pagination":{"total_items":3}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://contextforge.internal/admin/resources?include_inactive=true&page=1&per_page=100"))
                .andRespond(withSuccess("""
                        {"data":[{"id":"private-resource","name":"private-resource","visibility":"private"},{"id":"allowed-resource","name":"private-resource","visibility":"public"}],"pagination":{"total_items":2}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://contextforge.internal/admin/prompts?include_inactive=true&page=1&per_page=100"))
                .andRespond(withSuccess("""
                        {"data":[{"id":"private-prompt","name":"private-prompt","visibility":"private"},{"id":"allowed-prompt","name":"private-prompt","visibility":"public"}],"pagination":{"total_items":2}}
                        """, MediaType.APPLICATION_JSON));

        McpInternalServerResponse response = client.fetchInternalServers(
                new McpCatalogQuery(null, null, null, null, List.of(), 0, 24));

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().tools()).isEmpty();
        assertThat(response.items().getFirst().toolCount()).isZero();
        assertThat(response.items().getFirst().resources()).isEmpty();
        assertThat(response.items().getFirst().prompts()).isEmpty();
        assertThat(response.items().getFirst().resourceCount()).isZero();
        assertThat(response.items().getFirst().promptCount()).isZero();
        server.verify();
    }

    @Test
    void fetchInternalServersSearchesAcrossContextForgePagesBeforePaginating() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ContextForgeMcpProperties properties = new ContextForgeMcpProperties();
        properties.setBaseUrl("https://contextforge.internal");
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
        server.expect(once(), requestTo("https://contextforge.internal/admin/servers?include_inactive=false&page=1&per_page=100"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer ctx-token"))
                .andRespond(withSuccess("""
                        {
                          "data": [
                            {
                              "id": "senti",
                              "name": "senti_mcp",
                              "description": "舆情mcp",
                              "enabled": true,
                              "visibility": "private",
                              "associatedTools": [],
                              "associatedResources": [],
                              "associatedPrompts": [],
                              "tags": []
                            }
                          ],
                          "pagination": {
                            "page": 1,
                            "per_page": 100,
                            "total_items": 101,
                            "total_pages": 2
                          },
                          "links": null
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://contextforge.internal/admin/servers?include_inactive=false&page=2&per_page=100"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer ctx-token"))
                .andRespond(withSuccess("""
                        {
                          "data": [
                            {
                              "id": "34eaa0d257da49608da2c6b079ed0b5",
                              "name": "bdc4_group",
                              "description": "bdc4.0 MCP 服务",
                              "enabled": true,
                              "visibility": "public",
                              "associatedTools": [],
                              "associatedResources": [],
                              "associatedPrompts": [],
                              "tags": []
                            }
                          ],
                          "pagination": {
                            "page": 2,
                            "per_page": 100,
                            "total_items": 101,
                            "total_pages": 2
                          },
                          "links": null
                        }
                        """, MediaType.APPLICATION_JSON));

        McpInternalServerResponse response = client.fetchInternalServers(new McpCatalogQuery(
                "bdc",
                null,
                null,
                null,
                List.of(),
                0,
                24
        ));

        assertThat(response.total()).isEqualTo(1);
        assertThat(response.items())
                .extracting(McpInternalServerItemResponse::name)
                .containsExactly("bdc4_group");
        server.verify();
    }
}
