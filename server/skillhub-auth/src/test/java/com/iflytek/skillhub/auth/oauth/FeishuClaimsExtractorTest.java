package com.iflytek.skillhub.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import com.iflytek.skillhub.auth.policy.AccessDecision;
import com.iflytek.skillhub.auth.policy.EmailDomainAccessPolicy;
import com.iflytek.skillhub.auth.policy.ProviderAllowlistAccessPolicy;
import java.util.Set;

class FeishuClaimsExtractorTest {

    private final FeishuClaimsExtractor extractor = new FeishuClaimsExtractor();

    @Test
    void extract_usesTenantAndUnionIdAsStableSubject() {
        OAuthClaims claims = extractor.extract(request(), user(Map.of(
                "code", 0,
                "msg", "success",
                "data", Map.of(
                        "tenant_key", "tenant-a",
                        "union_id", "on-union",
                        "open_id", "ou-open",
                        "name", "Alice Zhang",
                        "avatar_url", "https://example.com/avatar.png",
                        "email", "alice@example.com"
                )
        )));

        assertThat(claims.provider()).isEqualTo("feishu");
        assertThat(claims.subject()).isEqualTo("tenant-a:on-union");
        assertThat(claims.providerLogin()).isEqualTo("Alice Zhang");
        assertThat(claims.email()).isEqualTo("alice@example.com");
        assertThat(claims.emailVerified()).isTrue();
        assertThat(claims.extra()).containsEntry("avatar_url", "https://example.com/avatar.png");
    }

    @Test
    void extract_fallsBackToOpenIdWhenUnionIdIsMissing() {
        OAuthClaims claims = extractor.extract(request(), user(Map.of(
                "code", 0,
                "data", Map.of(
                        "tenant_key", "tenant-a",
                        "open_id", "ou-open",
                        "name", "Bob Li"
                )
        )));

        assertThat(claims.subject()).isEqualTo("tenant-a:ou-open");
    }

    @Test
    void extract_fallsBackToEnterpriseEmailWhenEmailIsMissing() {
        OAuthClaims claims = extractor.extract(request(), user(Map.of(
                "code", 0,
                "data", Map.of(
                        "tenant_key", "tenant-a",
                        "union_id", "on-union",
                        "name", "Alice Zhang",
                        "enterprise_email", "alice@company.example"
                )
        )));

        assertThat(claims.email()).isEqualTo("alice@company.example");
    }

    @Test
    void extract_rejectsNonZeroFeishuResponseCode() {
        assertThatThrownBy(() -> extractor.extract(request(), user(Map.of(
                "code", 20021,
                "msg", "User resigned"
        ))))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("Feishu user info error");
    }

    @Test
    void extract_rejectsMissingStableIdentity() {
        assertThatThrownBy(() -> extractor.extract(request(), user(Map.of(
                "code", 0,
                "data", Map.of("name", "No Id")
        ))))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("missing stable identity");
    }

    @Test
    void extract_marksReturnedEmailAsVerified() {
        OAuthClaims claims = extractor.extract(request(), user(Map.of(
                "code", 0,
                "data", Map.of(
                        "tenant_key", "tenant-a",
                        "union_id", "on-union",
                        "email", "alice@company.example"
                )
        )));

        assertThat(new EmailDomainAccessPolicy(Set.of("company.example")).evaluate(claims))
                .isEqualTo(AccessDecision.ALLOW);
        assertThat(new ProviderAllowlistAccessPolicy(Set.of("feishu")).evaluate(claims))
                .isEqualTo(AccessDecision.ALLOW);
    }

    @Test
    void extract_rejectsMalformedResponseCode() {
        assertThatThrownBy(() -> extractor.extract(request(), user(Map.of("code", "not-a-number"))))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("Feishu user info error");
    }

    @Test
    void extract_rejectsNonStringDataKeys() {
        assertThatThrownBy(() -> extractor.extract(request(), user(Map.of(
                "code", 0,
                "data", Map.of(1, "invalid")
        ))))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("Feishu user info data is invalid");
    }

    private static OAuth2UserRequest request() {
        ClientRegistration registration = ClientRegistration.withRegistrationId("feishu")
                .clientId("cli_test")
                .clientSecret("secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://accounts.feishu.cn/open-apis/authen/v1/authorize")
                .tokenUri("https://accounts.feishu.cn/oauth/v3/token")
                .userInfoUri("https://open.feishu.cn/open-apis/authen/v1/user_info")
                .userNameAttributeName("code")
                .clientName("Feishu")
                .build();
        OAuth2AccessToken token = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "token",
                Instant.now(),
                Instant.now().plusSeconds(3600)
        );
        return new OAuth2UserRequest(registration, token);
    }

    private static DefaultOAuth2User user(Map<String, Object> attributes) {
        return new DefaultOAuth2User(List.of(), attributes, "code");
    }
}
