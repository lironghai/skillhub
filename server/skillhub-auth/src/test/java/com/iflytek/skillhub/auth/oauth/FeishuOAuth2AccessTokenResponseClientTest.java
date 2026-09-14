package com.iflytek.skillhub.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationExchange;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class FeishuOAuth2AccessTokenResponseClientTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(RestClient.Builder.class, RestClient::builder)
            .withUserConfiguration(FeishuOAuth2AccessTokenResponseClient.class);

    @Test
    void componentUsesRestClientBuilderConstructor() {
        contextRunner.run(context -> assertThat(context)
                .hasSingleBean(FeishuOAuth2AccessTokenResponseClient.class));
    }

    @Test
    void getTokenResponse_postsJsonToFeishuTokenEndpoint() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        server.expect(requestTo("https://accounts.feishu.cn/oauth/v3/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(content().json("""
                        {
                          "grant_type": "authorization_code",
                          "client_id": "cli_test",
                          "client_secret": "secret",
                          "code": "auth-code",
                          "redirect_uri": "https://skillhub.example.com/login/oauth2/code/feishu"
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "code": 0,
                          "access_token": "token-123",
                          "expires_in": 7200,
                          "refresh_token": "refresh-123",
                          "scope": "auth:user.id:read"
                        }
                        """, MediaType.APPLICATION_JSON));
        FeishuOAuth2AccessTokenResponseClient client =
                new FeishuOAuth2AccessTokenResponseClient(restClientBuilder.build());

        OAuth2AccessTokenResponse response = client.getTokenResponse(request());

        assertThat(response.getAccessToken().getTokenValue()).isEqualTo("token-123");
        assertThat(response.getAccessToken().getExpiresAt()).isNotNull();
        assertThat(response.getRefreshToken()).isNotNull();
        assertThat(response.getRefreshToken().getTokenValue()).isEqualTo("refresh-123");
        assertThat(response.getAccessToken().getScopes()).containsExactly("auth:user.id:read");
        server.verify();
    }

    @Test
    void getTokenResponse_rejectsNonZeroFeishuResponseCode() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        server.expect(requestTo("https://accounts.feishu.cn/oauth/v3/token"))
                .andRespond(withSuccess("""
                        {
                          "code": 99991663,
                          "msg": "invalid authorization code"
                        }
                        """, MediaType.APPLICATION_JSON));
        FeishuOAuth2AccessTokenResponseClient client =
                new FeishuOAuth2AccessTokenResponseClient(restClientBuilder.build());

        assertThatThrownBy(() -> client.getTokenResponse(request()))
                .isInstanceOf(OAuth2AuthorizationException.class)
                .hasMessageContaining("Feishu token exchange error");
        server.verify();
    }

    @Test
    void getTokenResponse_wrapsTokenEndpointFailureAsOAuthError() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        server.expect(requestTo("https://accounts.feishu.cn/oauth/v3/token"))
                .andRespond(withServerError());
        FeishuOAuth2AccessTokenResponseClient client =
                new FeishuOAuth2AccessTokenResponseClient(restClientBuilder.build());

        assertThatThrownBy(() -> client.getTokenResponse(request()))
                .isInstanceOfSatisfying(OAuth2AuthorizationException.class, ex -> {
                    assertThat(ex.getError().getErrorCode()).isEqualTo("feishu_token_exchange_error");
                    assertThat(ex.getError().getDescription()).isEqualTo("Feishu token exchange request failed");
                });
        server.verify();
    }

    private static OAuth2AuthorizationCodeGrantRequest request() {
        String redirectUri = "https://skillhub.example.com/login/oauth2/code/feishu";
        ClientRegistration registration = ClientRegistration.withRegistrationId("feishu")
                .clientId("cli_test")
                .clientSecret("secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("auth:user.id:read")
                .authorizationUri("https://accounts.feishu.cn/open-apis/authen/v1/authorize")
                .tokenUri("https://accounts.feishu.cn/oauth/v3/token")
                .userInfoUri("https://open.feishu.cn/open-apis/authen/v1/user_info")
                .userNameAttributeName("code")
                .clientName("Feishu")
                .build();
        OAuth2AuthorizationRequest authorizationRequest = OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://accounts.feishu.cn/open-apis/authen/v1/authorize")
                .clientId("cli_test")
                .redirectUri(redirectUri)
                .state("state-123")
                .scope("auth:user.id:read")
                .build();
        OAuth2AuthorizationResponse authorizationResponse = OAuth2AuthorizationResponse.success("auth-code")
                .redirectUri(redirectUri)
                .state("state-123")
                .build();
        return new OAuth2AuthorizationCodeGrantRequest(
                registration,
                new OAuth2AuthorizationExchange(authorizationRequest, authorizationResponse)
        );
    }
}
