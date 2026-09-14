package com.iflytek.skillhub.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.config.FeishuWebhookProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class FeishuWebhookNotificationSender {
    private static final Logger log = LoggerFactory.getLogger(FeishuWebhookNotificationSender.class);
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final FeishuWebhookProperties properties;

    public FeishuWebhookNotificationSender(RestClient.Builder builder,
                                           ObjectMapper objectMapper,
                                           FeishuWebhookProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());
        this.restClient = builder.requestFactory(requestFactory).build();
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public void send(FeishuWebhookNotificationPayload payload) {
        if (!properties.isEnabled() || !StringUtils.hasText(properties.getUrl())) {
            return;
        }
        try {
            String body = objectMapper.writeValueAsString(payload);
            var request = restClient.post()
                    .uri(properties.getUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body);
            if (StringUtils.hasText(properties.getHeaderName())
                    && StringUtils.hasText(properties.getHeaderValue())) {
                request.header(properties.getHeaderName(), properties.getHeaderValue());
            }
            if (StringUtils.hasText(properties.getSecret())) {
                request.header("X-SkillHub-Signature", signature(body));
            }
            request.retrieve().toBodilessEntity();
        } catch (JsonProcessingException | RestClientException ex) {
            log.warn("Failed to send Feishu subscription notification webhook", ex);
        }
    }

    private String signature(String body) {
        if (!StringUtils.hasText(properties.getSecret())) {
            return "";
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to sign Feishu webhook payload", ex);
        }
    }
}
