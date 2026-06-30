package com.iflytek.skillhub.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class ContextForgeMcpCatalogClient {
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ContextForgeMcpProperties properties;
    private final Clock clock;
    private volatile CachedToken cachedToken;

    @Autowired
    public ContextForgeMcpCatalogClient(RestClient.Builder restClientBuilder,
                                        ObjectMapper objectMapper,
                                        ContextForgeMcpProperties properties) {
        this(restClientBuilder.build(), objectMapper, properties, Clock.systemUTC());
    }

    ContextForgeMcpCatalogClient(RestClient restClient,
                                 ObjectMapper objectMapper,
                                 ContextForgeMcpProperties properties) {
        this(restClient, objectMapper, properties, Clock.systemUTC());
    }

    ContextForgeMcpCatalogClient(RestClient restClient,
                                 ObjectMapper objectMapper,
                                 ContextForgeMcpProperties properties,
                                 Clock clock) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.clock = clock;
    }

    public McpCatalogResponse fetchServers(McpCatalogQuery query) {
        validateConfigured();
        String accessToken = getAccessToken();
        String body = getCatalog(accessToken, query);
        return parseCatalog(body, query);
    }

    public McpInternalServerResponse fetchInternalServers(McpCatalogQuery query) {
        validateConfigured();
        String accessToken = getAccessToken();
        String body = getInternalServers(accessToken, query);
        return parseInternalServers(body, query);
    }

    private String getAccessToken() {
        CachedToken token = cachedToken;
        if (token != null && token.isValid(clock.instant())) {
            return token.value();
        }
        synchronized (this) {
            token = cachedToken;
            if (token != null && token.isValid(clock.instant())) {
                return token.value();
            }
            cachedToken = login();
            return cachedToken.value();
        }
    }

    private CachedToken login() {
        try {
            String body = restClient.post()
                    .uri(endpoint(properties.getLoginPath()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new LoginRequest(properties.getUsername(), properties.getPassword()))
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(body);
            String accessToken = root.path("access_token").asText();
            if (!StringUtils.hasText(accessToken)) {
                throw new McpCatalogUnavailableException("ContextForge login response did not contain an access_token");
            }
            return new CachedToken(accessToken, expiresAt(root.path("expires_in").asLong(0)));
        } catch (RestClientException | JsonProcessingException ex) {
            throw new McpCatalogUnavailableException("Unable to login to ContextForge", ex);
        }
    }

    private Instant expiresAt(long expiresInSeconds) {
        long usableSeconds = expiresInSeconds > 60 ? expiresInSeconds - 60 : 300;
        return clock.instant().plus(Duration.ofSeconds(usableSeconds));
    }

    private String getCatalog(String accessToken, McpCatalogQuery query) {
        try {
            return restClient.get()
                    .uri(catalogUri(query))
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException ex) {
            throw new McpCatalogUnavailableException("Unable to fetch MCP catalog from ContextForge", ex);
        }
    }

    private String getInternalServers(String accessToken, McpCatalogQuery query) {
        try {
            return restClient.get()
                    .uri(internalServersUri(query))
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException ex) {
            throw new McpCatalogUnavailableException("Unable to fetch internal MCP servers from ContextForge", ex);
        }
    }

    private String catalogUri(McpCatalogQuery query) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(endpoint(properties.getCatalogPath()))
                .queryParam("show_available_only", "false")
                .queryParam("limit", query.size())
                .queryParam("offset", Math.max(query.page(), 0) * Math.max(query.size(), 1));

        addParam(builder, "search", query.search());
        addParam(builder, "category", query.category());
        addParam(builder, "auth_type", query.authType());
        addParam(builder, "provider", query.provider());
        for (String tag : safeList(query.tags())) {
            addParam(builder, "tags", tag);
        }
        return builder.build().toUriString();
    }

    private String internalServersUri(McpCatalogQuery query) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(endpoint(properties.getInternalServersPath()))
                .queryParam("include_inactive", "true")
                .queryParam("page", Math.max(query.page(), 0) + 1)
                .queryParam("per_page", query.size());

        addParam(builder, "search", query.search());
        return builder.build().toUriString();
    }

    private McpCatalogResponse parseCatalog(String body, McpCatalogQuery query) {
        try {
            JsonNode root = objectMapper.readTree(body);
            List<McpCatalogItemResponse> items = new ArrayList<>();
            for (JsonNode server : root.path("servers")) {
                items.add(new McpCatalogItemResponse(
                        text(server, "id"),
                        text(server, "name"),
                        text(server, "category"),
                        text(server, "provider"),
                        text(server, "description"),
                        text(server, "url"),
                        text(server, "auth_type"),
                        bool(server, "requires_api_key"),
                        bool(server, "secure"),
                        textList(server, "tags"),
                        text(server, "transport"),
                        text(server, "logo_url"),
                        text(server, "documentation_url"),
                        bool(server, "is_registered"),
                        bool(server, "is_available"),
                        bool(server, "requires_oauth_config")
                ));
            }
            return new McpCatalogResponse(
                    List.copyOf(items),
                    root.path("total").asLong(items.size()),
                    query.page(),
                    query.size(),
                    textList(root, "categories"),
                    textList(root, "auth_types"),
                    textList(root, "providers"),
                    textList(root, "all_tags")
            );
        } catch (JsonProcessingException ex) {
            throw new McpCatalogUnavailableException("Unable to parse MCP catalog from ContextForge", ex);
        }
    }

    private McpInternalServerResponse parseInternalServers(String body, McpCatalogQuery query) {
        try {
            JsonNode root = objectMapper.readTree(body);
            List<McpInternalServerItemResponse> items = new ArrayList<>();
            for (JsonNode server : root.path("data")) {
                String id = text(server, "id");
                items.add(new McpInternalServerItemResponse(
                        id,
                        text(server, "name"),
                        text(server, "description"),
                        server.path("enabled").asBoolean(false),
                        text(server, "visibility"),
                        firstText(server, "ownerEmail", "owner_email"),
                        text(server, "team"),
                        countArray(server, "associatedTools", "associated_tools"),
                        countArray(server, "associatedResources", "associated_resources"),
                        countArray(server, "associatedPrompts", "associated_prompts"),
                        tagNames(server.path("tags")),
                        publicEndpoint(id, "mcp"),
                        publicEndpoint(id, "sse")
                ));
            }
            JsonNode pagination = root.path("pagination");
            long total = pagination.path("total_items").asLong(items.size());
            return new McpInternalServerResponse(
                    List.copyOf(items),
                    total,
                    query.page(),
                    query.size()
            );
        } catch (JsonProcessingException ex) {
            throw new McpCatalogUnavailableException("Unable to parse internal MCP servers from ContextForge", ex);
        }
    }

    private void validateConfigured() {
        if (!properties.isEnabled()) {
            throw new McpCatalogUnavailableException("ContextForge MCP catalog proxy is disabled");
        }
        if (!StringUtils.hasText(properties.getBaseUrl())
                || !StringUtils.hasText(properties.getUsername())
                || !StringUtils.hasText(properties.getPassword())) {
            throw new McpCatalogUnavailableException("ContextForge MCP catalog proxy is not configured");
        }
    }

    private String endpoint(String path) {
        String baseUrl = properties.getBaseUrl().trim();
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        String normalizedPath = path == null ? "" : path.trim();
        while (normalizedPath.startsWith("/")) {
            normalizedPath = normalizedPath.substring(1);
        }
        return baseUrl + "/" + normalizedPath;
    }

    private void addParam(UriComponentsBuilder builder, String name, String value) {
        if (StringUtils.hasText(value)) {
            builder.queryParam(name, value.trim());
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    private boolean bool(JsonNode node, String field) {
        return node.path(field).asBoolean(false);
    }

    private String firstText(JsonNode node, String firstField, String secondField) {
        String first = text(node, firstField);
        return StringUtils.hasText(first) ? first : text(node, secondField);
    }

    private int countArray(JsonNode node, String firstField, String secondField) {
        JsonNode first = node.path(firstField);
        if (first.isArray()) {
            return first.size();
        }
        JsonNode second = node.path(secondField);
        return second.isArray() ? second.size() : 0;
    }

    private List<String> textList(JsonNode node, String field) {
        List<String> values = new ArrayList<>();
        JsonNode array = node.path(field);
        if (array.isArray()) {
            for (JsonNode item : array) {
                if (item.isTextual() && StringUtils.hasText(item.asText())) {
                    values.add(item.asText());
                }
            }
        }
        return List.copyOf(values);
    }

    private List<String> tagNames(JsonNode tags) {
        List<String> values = new ArrayList<>();
        if (tags.isArray()) {
            for (JsonNode tag : tags) {
                if (tag.isTextual() && StringUtils.hasText(tag.asText())) {
                    values.add(tag.asText());
                } else {
                    String name = text(tag, "name");
                    if (StringUtils.hasText(name)) {
                        values.add(name);
                    }
                }
            }
        }
        return List.copyOf(values);
    }

    private String publicEndpoint(String serverId, String suffix) {
        if (!StringUtils.hasText(serverId)) {
            return null;
        }
        String baseUrl = StringUtils.hasText(properties.getPublicBaseUrl())
                ? properties.getPublicBaseUrl()
                : properties.getBaseUrl();
        baseUrl = baseUrl.trim();
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + "/servers/" + serverId + "/" + suffix;
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private record LoginRequest(String email, String password) {
    }

    private record CachedToken(String value, Instant expiresAt) {
        boolean isValid(Instant now) {
            return StringUtils.hasText(value) && expiresAt.isAfter(now);
        }
    }
}
