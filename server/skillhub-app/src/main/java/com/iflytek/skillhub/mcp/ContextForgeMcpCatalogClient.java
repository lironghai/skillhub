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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
        AssociatedCatalog associatedCatalog = fetchAssociatedCatalog(accessToken, body);
        return parseInternalServers(body, query, associatedCatalog);
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

    private AssociatedCatalog fetchAssociatedCatalog(String accessToken, String internalServersBody) {
        AssociatedNeeds needs = associatedNeeds(internalServersBody);
        return new AssociatedCatalog(
                needs.hasTools() ? associatedCatalogItems(accessToken, properties.getInternalToolsPath()) : Map.of(),
                needs.hasResources() ? associatedCatalogItems(accessToken, properties.getInternalResourcesPath()) : Map.of(),
                needs.hasPrompts() ? associatedCatalogItems(accessToken, properties.getInternalPromptsPath()) : Map.of()
        );
    }

    private AssociatedNeeds associatedNeeds(String internalServersBody) {
        try {
            JsonNode root = objectMapper.readTree(internalServersBody);
            boolean hasTools = false;
            boolean hasResources = false;
            boolean hasPrompts = false;
            for (JsonNode server : root.path("data")) {
                hasTools = hasTools || hasArrayItems(server, "associatedTools", "associated_tools");
                hasResources = hasResources || hasArrayItems(server, "associatedResources", "associated_resources");
                hasPrompts = hasPrompts || hasArrayItems(server, "associatedPrompts", "associated_prompts");
            }
            return new AssociatedNeeds(hasTools, hasResources, hasPrompts);
        } catch (JsonProcessingException ex) {
            throw new McpCatalogUnavailableException("Unable to parse internal MCP servers from ContextForge", ex);
        }
    }

    private boolean hasArrayItems(JsonNode node, String firstField, String secondField) {
        JsonNode first = node.path(firstField);
        if (first.isArray() && first.size() > 0) {
            return true;
        }
        JsonNode second = node.path(secondField);
        return second.isArray() && second.size() > 0;
    }

    private Map<String, McpAssociatedItemResponse> associatedCatalogItems(String accessToken, String path) {
        try {
            String body = restClient.get()
                    .uri(associatedCatalogUri(path))
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(body);
            Map<String, McpAssociatedItemResponse> items = new HashMap<>();
            for (JsonNode item : root.path("data")) {
                McpAssociatedItemResponse associatedItem = associatedItem(item);
                putAssociatedItem(items, associatedItem.id(), associatedItem);
                putAssociatedItem(items, associatedItem.name(), associatedItem);
                putAssociatedItem(items, text(item, "displayName"), associatedItem);
                putAssociatedItem(items, text(item, "display_name"), associatedItem);
            }
            return Map.copyOf(items);
        } catch (RestClientException | JsonProcessingException ex) {
            throw new McpCatalogUnavailableException("Unable to fetch internal MCP associated item details from ContextForge", ex);
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

    private String associatedCatalogUri(String path) {
        return UriComponentsBuilder.fromHttpUrl(endpoint(path))
                .queryParam("include_inactive", "true")
                .queryParam("page", 1)
                .queryParam("per_page", Math.max(properties.getMaxSize(), 1))
                .build()
                .toUriString();
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

    private McpInternalServerResponse parseInternalServers(String body, McpCatalogQuery query, AssociatedCatalog associatedCatalog) {
        try {
            JsonNode root = objectMapper.readTree(body);
            List<McpInternalServerItemResponse> items = new ArrayList<>();
            for (JsonNode server : root.path("data")) {
                if (!matchesInternalServerSearch(server, query.search())) {
                    continue;
                }
                String id = text(server, "id");
                List<McpAssociatedItemResponse> tools = associatedItems(
                        server,
                        "associatedTools",
                        "associated_tools",
                        "associatedToolIds",
                        "associated_tool_ids",
                        associatedCatalog.tools()
                );
                items.add(new McpInternalServerItemResponse(
                        id,
                        text(server, "name"),
                        text(server, "description"),
                        firstText(server, "icon", "iconUrl"),
                        server.path("enabled").asBoolean(false),
                        text(server, "visibility"),
                        firstText(server, "ownerEmail", "owner_email"),
                        text(server, "team"),
                        countArray(server, "associatedTools", "associated_tools"),
                        countArray(server, "associatedResources", "associated_resources"),
                        countArray(server, "associatedPrompts", "associated_prompts"),
                        tools,
                        associatedItems(server, "associatedResources", "associated_resources", null, null, associatedCatalog.resources()),
                        associatedItems(server, "associatedPrompts", "associated_prompts", null, null, associatedCatalog.prompts()),
                        tagNames(server.path("tags")),
                        publicEndpoint(id, "mcp"),
                        publicEndpoint(id, "sse")
                ));
            }
            JsonNode pagination = root.path("pagination");
            long total = StringUtils.hasText(query.search())
                    ? items.size()
                    : pagination.path("total_items").asLong(items.size());
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

    private boolean matchesInternalServerSearch(JsonNode server, String search) {
        if (!StringUtils.hasText(search)) {
            return true;
        }
        String normalizedSearch = search.trim().toLowerCase(Locale.ROOT);
        return containsIgnoreCase(text(server, "id"), normalizedSearch)
                || containsIgnoreCase(text(server, "name"), normalizedSearch)
                || containsIgnoreCase(text(server, "description"), normalizedSearch);
    }

    private boolean containsIgnoreCase(String value, String normalizedSearch) {
        return StringUtils.hasText(value)
                && value.toLowerCase(Locale.ROOT).contains(normalizedSearch);
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

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private int countArray(JsonNode node, String firstField, String secondField) {
        JsonNode first = node.path(firstField);
        if (first.isArray()) {
            return first.size();
        }
        JsonNode second = node.path(secondField);
        return second.isArray() ? second.size() : 0;
    }

    private List<McpAssociatedItemResponse> associatedItems(JsonNode node,
                                                           String firstField,
                                                           String secondField,
                                                           String firstIdField,
                                                           String secondIdField,
                                                           Map<String, McpAssociatedItemResponse> catalog) {
        JsonNode array = node.path(firstField);
        if (!array.isArray()) {
            array = node.path(secondField);
        }
        if (!array.isArray()) {
            return List.of();
        }

        List<String> ids = textValues(node, firstIdField, secondIdField);
        List<McpAssociatedItemResponse> values = new ArrayList<>();
        int index = 0;
        for (JsonNode item : array) {
            String id = index < ids.size() ? ids.get(index) : null;
            McpAssociatedItemResponse associatedItem = associatedItem(item, id);
            McpAssociatedItemResponse enriched = firstCatalogMatch(catalog, associatedItem.id(), associatedItem.name());
            values.add(enriched != null ? enriched : associatedItem);
            index++;
        }
        return List.copyOf(values);
    }

    private McpAssociatedItemResponse firstCatalogMatch(Map<String, McpAssociatedItemResponse> catalog, String firstKey, String secondKey) {
        McpAssociatedItemResponse item = catalog.get(firstKey);
        if (item != null) {
            return item;
        }
        return catalog.get(secondKey);
    }

    private McpAssociatedItemResponse associatedItem(JsonNode item) {
        return associatedItem(item, null);
    }

    private McpAssociatedItemResponse associatedItem(JsonNode item, String fallbackId) {
        if (item.isTextual()) {
            String value = item.asText();
            String id = StringUtils.hasText(fallbackId) ? fallbackId : value;
            return new McpAssociatedItemResponse(id, value, null);
        }

        if (item.isObject()) {
            String id = firstText(item, "id", "uuid");
            if (!StringUtils.hasText(id)) {
                id = fallbackId;
            }

            String name = firstText(item, "name", "title");
            if (!StringUtils.hasText(name)) {
                name = firstText(item, "displayName", "display_name");
            }
            String description = text(item, "description");
            if (!StringUtils.hasText(name)) {
                name = id;
            }
            return new McpAssociatedItemResponse(
                    id,
                    name,
                    description,
                    object(item, "inputSchema"),
                    object(item, "outputSchema")
            );
        }

        return new McpAssociatedItemResponse(fallbackId, fallbackId, null);
    }

    private JsonNode object(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isObject() ? value.deepCopy() : null;
    }

    private void putAssociatedItem(Map<String, McpAssociatedItemResponse> items, String key, McpAssociatedItemResponse item) {
        if (StringUtils.hasText(key) && item != null) {
            items.put(key, item);
        }
    }

    private List<String> textValues(JsonNode node, String firstField, String secondField) {
        List<String> values = new ArrayList<>();
        if (StringUtils.hasText(firstField)) {
            addTextValues(values, node.path(firstField));
        }
        if (values.isEmpty() && StringUtils.hasText(secondField)) {
            addTextValues(values, node.path(secondField));
        }
        return List.copyOf(values);
    }

    private void addTextValues(List<String> values, JsonNode array) {
        if (!array.isArray()) {
            return;
        }
        for (JsonNode item : array) {
            if (item.isTextual() && StringUtils.hasText(item.asText())) {
                values.add(item.asText());
            }
        }
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

    private record AssociatedCatalog(
            Map<String, McpAssociatedItemResponse> tools,
            Map<String, McpAssociatedItemResponse> resources,
            Map<String, McpAssociatedItemResponse> prompts
    ) {
    }

    private record AssociatedNeeds(boolean hasTools, boolean hasResources, boolean hasPrompts) {
    }
}
