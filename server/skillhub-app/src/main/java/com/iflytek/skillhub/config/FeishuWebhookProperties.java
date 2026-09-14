package com.iflytek.skillhub.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "skillhub.notification.feishu-webhook")
public class FeishuWebhookProperties {
    private boolean enabled;
    private String url;
    private String secret;
    private String headerName;
    private String headerValue;
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration readTimeout = Duration.ofSeconds(5);
    private int corePoolSize = 4;
    private int maxPoolSize = 8;
    private int queueCapacity = 200;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
    public String getHeaderName() { return headerName; }
    public void setHeaderName(String headerName) { this.headerName = headerName; }
    public String getHeaderValue() { return headerValue; }
    public void setHeaderValue(String headerValue) { this.headerValue = headerValue; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
    public int getCorePoolSize() { return corePoolSize; }
    public void setCorePoolSize(int corePoolSize) { this.corePoolSize = corePoolSize; }
    public int getMaxPoolSize() { return maxPoolSize; }
    public void setMaxPoolSize(int maxPoolSize) { this.maxPoolSize = maxPoolSize; }
    public int getQueueCapacity() { return queueCapacity; }
    public void setQueueCapacity(int queueCapacity) { this.queueCapacity = queueCapacity; }
}
