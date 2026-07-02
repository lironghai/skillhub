package com.iflytek.skillhub.auth.oauth;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

@Component
public class FeishuClaimsExtractor implements OAuthClaimsExtractor {

    private static final Logger log = LoggerFactory.getLogger(FeishuClaimsExtractor.class);

    @Override
    public String getProvider() {
        return "feishu";
    }

    @Override
    public OAuthClaims extract(OAuth2UserRequest request, OAuth2User oAuth2User) {
        Map<String, Object> attrs = oAuth2User.getAttributes();
        int code = asInt(attrs.get("code"));
        if (code != 0) {
            throw new OAuth2AuthenticationException(new OAuth2Error(
                    "feishu_user_info_error",
                    "Feishu user info error: " + attrs.getOrDefault("msg", code),
                    null
            ));
        }

        Map<String, Object> data = asMap(attrs.get("data"));
        String tenantKey = asString(data.get("tenant_key"));
        String unionId = asString(data.get("union_id"));
        String openId = asString(data.get("open_id"));
        String email = asString(data.get("email"));
        String enterpriseEmail = asString(data.get("enterprise_email"));
        log.info("Feishu OAuth user_info data received - keys: {}, emailPresent: {}, enterpriseEmailPresent: {}",
                new TreeSet<>(data.keySet()), email != null, enterpriseEmail != null);

        String stableId = firstPresent(unionId, openId);
        if (tenantKey == null || stableId == null) {
            throw new OAuth2AuthenticationException(new OAuth2Error(
                    "feishu_missing_identity",
                    "Feishu user info missing stable identity",
                    null
            ));
        }

        String providerLogin = firstPresent(
                asString(data.get("name")),
                asString(data.get("en_name")),
                asString(data.get("user_id")),
                stableId
        );

        return new OAuthClaims(
                "feishu",
                tenantKey + ":" + stableId,
                firstPresent(email, enterpriseEmail),
                false,
                providerLogin,
                new HashMap<>(data)
        );
    }

    private static int asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return Integer.parseInt(stringValue);
        }
        return -1;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
    }

    private static String asString(Object value) {
        return value instanceof String stringValue && !stringValue.isBlank() ? stringValue : null;
    }

    private static String firstPresent(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
