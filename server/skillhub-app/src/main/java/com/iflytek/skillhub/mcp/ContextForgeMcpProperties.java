package com.iflytek.skillhub.mcp;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "skillhub.mcp.context-forge")
public class ContextForgeMcpProperties {
    private boolean enabled = true;
    private String baseUrl = "http://localhost:4444";
    private String publicBaseUrl = "http://localhost:4444";
    private String username;
    private String password;
    private String teamId;
    private String loginPath = "/auth/email/login";
    private String catalogPath = "/admin/mcp-registry/servers";
    private String internalServersPath = "/v1/servers";
    private String internalToolsPath = "/admin/tools";
    private String internalResourcesPath = "/admin/resources";
    private String internalPromptsPath = "/admin/prompts";
    private int defaultSize = 24;
    private int maxSize = 100;
    private int maxSearchScanSize = 1000;
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(15);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getTeamId() {
        return teamId;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId;
    }

    public String getLoginPath() {
        return loginPath;
    }

    public void setLoginPath(String loginPath) {
        this.loginPath = loginPath;
    }

    public String getCatalogPath() {
        return catalogPath;
    }

    public void setCatalogPath(String catalogPath) {
        this.catalogPath = catalogPath;
    }

    public String getInternalServersPath() {
        return internalServersPath;
    }

    public void setInternalServersPath(String internalServersPath) {
        this.internalServersPath = internalServersPath;
    }

    public String getInternalToolsPath() {
        return internalToolsPath;
    }

    public void setInternalToolsPath(String internalToolsPath) {
        this.internalToolsPath = internalToolsPath;
    }

    public String getInternalResourcesPath() {
        return internalResourcesPath;
    }

    public void setInternalResourcesPath(String internalResourcesPath) {
        this.internalResourcesPath = internalResourcesPath;
    }

    public String getInternalPromptsPath() {
        return internalPromptsPath;
    }

    public void setInternalPromptsPath(String internalPromptsPath) {
        this.internalPromptsPath = internalPromptsPath;
    }

    public int getDefaultSize() {
        return defaultSize;
    }

    public void setDefaultSize(int defaultSize) {
        this.defaultSize = defaultSize;
    }

    public int getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(int maxSize) {
        this.maxSize = maxSize;
    }

    public int getMaxSearchScanSize() {
        return maxSearchScanSize;
    }

    public void setMaxSearchScanSize(int maxSearchScanSize) {
        this.maxSearchScanSize = maxSearchScanSize;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }
}
