package com.iflytek.skillhub.listener;

import java.util.List;

public record FeishuWebhookNotificationPayload(
        String eventType,
        String skillSlug,
        String skillVersion,
        String text,
        List<Recipient> recipients) {
    public record Recipient(String subject, String email) {}
}
