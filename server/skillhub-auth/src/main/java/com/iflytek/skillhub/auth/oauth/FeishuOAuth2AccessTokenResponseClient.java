package com.iflytek.skillhub.auth.oauth;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.endpoint.DefaultAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class FeishuOAuth2AccessTokenResponseClient
        implements OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> {

    private final OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> delegate =
            new DefaultAuthorizationCodeTokenResponseClient();
    private final RestClient restClient;

    public FeishuOAuth2AccessTokenResponseClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    @Override
    public OAuth2AccessTokenResponse getTokenResponse(OAuth2AuthorizationCodeGrantRequest grantRequest) {
        if (!"feishu".equals(grantRequest.getClientRegistration().getRegistrationId())) {
            return delegate.getTokenResponse(grantRequest);
        }

        FeishuTokenResponse response = restClient.post()
                .uri(grantRequest.getClientRegistration().getProviderDetails().getTokenUri())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "grant_type", "authorization_code",
                        "client_id", grantRequest.getClientRegistration().getClientId(),
                        "client_secret", grantRequest.getClientRegistration().getClientSecret(),
                        "code", grantRequest.getAuthorizationExchange().getAuthorizationResponse().getCode(),
                        "redirect_uri", grantRequest.getAuthorizationExchange().getAuthorizationResponse().getRedirectUri()
                ))
                .retrieve()
                .body(FeishuTokenResponse.class);

        if (response == null || response.code() != 0 || response.accessToken() == null || response.accessToken().isBlank()) {
            String message = response == null ? "empty response" : firstPresent(response.msg(), String.valueOf(response.code()));
            throw new OAuth2AuthorizationException(new OAuth2Error(
                    "feishu_token_exchange_error",
                    "Feishu token exchange error: " + message,
                    null
            ));
        }

        OAuth2AccessTokenResponse.Builder builder = OAuth2AccessTokenResponse.withToken(response.accessToken())
                .tokenType(OAuth2AccessToken.TokenType.BEARER)
                .expiresIn(response.expiresIn() == null ? 0 : response.expiresIn())
                .scopes(parseScopes(response.scope()));
        if (response.refreshToken() != null && !response.refreshToken().isBlank()) {
            builder.refreshToken(response.refreshToken());
        }
        return builder.build();
    }

    private static Set<String> parseScopes(String scope) {
        if (scope == null || scope.isBlank()) {
            return Set.of();
        }
        return new LinkedHashSet<>(Arrays.asList(scope.trim().split("\\s+")));
    }

    private static String firstPresent(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private record FeishuTokenResponse(
            int code,
            String msg,
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") Long expiresIn,
            @JsonProperty("refresh_token") String refreshToken,
            String scope
    ) {}
}
