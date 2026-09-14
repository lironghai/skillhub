package com.iflytek.skillhub.mcp;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.regex.Pattern;

@Service
@ConditionalOnProperty(prefix = "skillhub.mcp", name = {"enabled", "context-forge.enabled"}, havingValue = "true", matchIfMissing = true)
public class McpCatalogService {
    private static final Pattern NON_NEGATIVE_INTEGER = Pattern.compile("\\d+");

    private final ContextForgeMcpCatalogClient catalogClient;
    private final ContextForgeMcpProperties properties;

    public McpCatalogService(ContextForgeMcpCatalogClient catalogClient, ContextForgeMcpProperties properties) {
        this.catalogClient = catalogClient;
        this.properties = properties;
    }

    public McpCatalogResponse search(String search,
                                     String category,
                                     String authType,
                                     String provider,
                                     List<String> tags,
                                     String page,
                                     String size) {
        int normalizedPage = normalizedPage(page);
        int normalizedSize = normalizedSize(size);
        return catalogClient.fetchServers(new McpCatalogQuery(
                trimToNull(search),
                trimToNull(category),
                trimToNull(authType),
                trimToNull(provider),
                normalizeTags(tags),
                normalizedPage,
                normalizedSize
        ));
    }

    public McpInternalServerResponse internalServers(String search, String page, String size) {
        return catalogClient.fetchInternalServers(new McpCatalogQuery(
                trimToNull(search),
                null,
                null,
                null,
                List.of(),
                normalizedPage(page),
                normalizedSize(size)
        ));
    }

    private int normalizedPage(String page) {
        return parseNonNegativeInt(page, 0);
    }

    private int normalizedSize(String size) {
        return Math.min(
                parsePositiveInt(size, Math.max(properties.getDefaultSize(), 1)),
                Math.max(properties.getMaxSize(), 1)
        );
    }

    private int parseNonNegativeInt(String rawValue, int defaultValue) {
        if (!StringUtils.hasText(rawValue)) {
            return defaultValue;
        }
        String normalized = rawValue.trim();
        if (!NON_NEGATIVE_INTEGER.matcher(normalized).matches()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private int parsePositiveInt(String rawValue, int defaultValue) {
        int parsed = parseNonNegativeInt(rawValue, defaultValue);
        return parsed > 0 ? parsed : defaultValue;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null) {
            return List.of();
        }
        return tags.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }
}
