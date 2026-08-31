package com.iflytek.skillhub.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SensitiveLogSanitizerTest {

    private final SensitiveLogSanitizer sanitizer = new SensitiveLogSanitizer();

    @Test
    void shouldRedactSensitiveQueryParameters() {
        String sanitized = sanitizer.sanitizeQuery(
                "returnTo=%2Fdashboard&token=abc123&password=secret&code=xyz&state=nonce"
                        + "&access_token=access&id_token=id&client_secret=client&client_assertion=assertion");

        assertThat(sanitized).contains("returnTo=%2Fdashboard");
        assertThat(sanitized).contains("token=[REDACTED]");
        assertThat(sanitized).contains("password=[REDACTED]");
        assertThat(sanitized).contains("code=[REDACTED]");
        assertThat(sanitized).contains("state=[REDACTED]");
        assertThat(sanitized).contains("access_token=[REDACTED]");
        assertThat(sanitized).contains("id_token=[REDACTED]");
        assertThat(sanitized).contains("client_secret=[REDACTED]");
        assertThat(sanitized).contains("client_assertion=[REDACTED]");
    }

    @Test
    void shouldRedactUrlEncodedSensitiveParameterNames() {
        String sanitized = sanitizer.sanitizeQuery(
                "%74oken=abc123&access%5Ftoken=access&client%5fsecret=client&returnTo=%2Fdashboard");

        assertThat(sanitized).contains("%74oken=[REDACTED]");
        assertThat(sanitized).contains("access%5Ftoken=[REDACTED]");
        assertThat(sanitized).contains("client%5fsecret=[REDACTED]");
        assertThat(sanitized).contains("returnTo=%2Fdashboard");
        assertThat(sanitized).doesNotContain("abc123", "=access", "=client");
    }

    @Test
    void shouldLeaveMalformedEncodedParameterNamesUnchanged() {
        assertThat(sanitizer.sanitizeQuery("bad%2=value&returnTo=%2Fdashboard"))
                .isEqualTo("bad%2=value&returnTo=%2Fdashboard");
    }
}
