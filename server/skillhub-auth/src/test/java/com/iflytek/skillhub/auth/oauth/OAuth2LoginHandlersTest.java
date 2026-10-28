package com.iflytek.skillhub.auth.oauth;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OAuth2LoginHandlersTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @CsvSource({
            "'', /space/global/李白-skill, /space/global/%E6%9D%8E%E7%99%BD-skill",
            "/skillhub, /space/global/李白-skill, /skillhub/space/global/%E6%9D%8E%E7%99%BD-skill",
            "'', /space/global/%E6%9D%8E%E7%99%BD-skill, /space/global/%E6%9D%8E%E7%99%BD-skill",
            "'', /space/global/李白-skill?q=中文+skill#说明, /space/global/%E6%9D%8E%E7%99%BD-skill?q=%E4%B8%AD%E6%96%87+skill#%E8%AF%B4%E6%98%8E",
            "'', /space/global/café-🚀, /space/global/caf%C3%A9-%F0%9F%9A%80",
            "'', /space/global/李白-skill?returnTo=%2Fsearch%3Fq%3D%26sort%3Dnewest%26page%3D0%26starredOnly%3Dfalse, /space/global/%E6%9D%8E%E7%99%BD-skill?returnTo=%2Fsearch%3Fq%3D%26sort%3Dnewest%26page%3D0%26starredOnly%3Dfalse"
    })
    void successHandler_encodesUnicodeWithoutChangingExistingEscapes(String contextPath,
                                                                    String target,
                                                                    String expected) throws Exception {
        OAuthLoginFlowService flowService = new OAuthLoginFlowService(
                List.of(),
                mock(com.iflytek.skillhub.auth.policy.AccessPolicy.class),
                mock(com.iflytek.skillhub.auth.identity.IdentityBindingService.class)
        );
        OAuth2LoginSuccessHandler handler = new OAuth2LoginSuccessHandler(
                new com.iflytek.skillhub.auth.session.PlatformSessionService(), flowService
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContextPath(contextPath);
        request.setParameter("returnTo", target);
        flowService.rememberReturnTo(request);
        MockHttpServletResponse response = new MockHttpServletResponse();
        Authentication authentication = new UsernamePasswordAuthenticationToken("user", null, List.of());

        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getHeader("Location")).isEqualTo(expected);
        assertThat(response.getHeader("Location").chars().allMatch(character -> character < 128)).isTrue();
        assertThat(request.getSession().getAttribute(OAuthLoginRedirectSupport.SESSION_RETURN_TO_ATTRIBUTE)).isNull();
    }

    @Test
    void successHandler_redirectsToStoredReturnTo() throws Exception {
        OAuthLoginFlowService oauthLoginFlowService = mock(OAuthLoginFlowService.class);
        OAuth2LoginSuccessHandler handler = new OAuth2LoginSuccessHandler(
                new com.iflytek.skillhub.auth.session.PlatformSessionService(),
                oauthLoginFlowService
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        HttpSession session = request.getSession(true);
        String originalSessionId = session.getId();
        session.setAttribute(OAuthLoginRedirectSupport.SESSION_RETURN_TO_ATTRIBUTE, "/dashboard/publish");

        var principal = new com.iflytek.skillhub.auth.rbac.PlatformPrincipal(
                "user-1", "User", "user@example.com", null, "github", Set.of()
        );
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new DefaultOAuth2User(List.of(), Map.of("platformPrincipal", principal, "login", "user"), "login"),
                null,
                List.of()
        );

        org.mockito.Mockito.when(oauthLoginFlowService.consumeReturnTo(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    HttpSession currentSession = invocation.getArgument(0);
                    Object value = currentSession.getAttribute(OAuthLoginRedirectSupport.SESSION_RETURN_TO_ATTRIBUTE);
                    currentSession.removeAttribute(OAuthLoginRedirectSupport.SESSION_RETURN_TO_ATTRIBUTE);
                    return value;
                });

        handler.onAuthenticationSuccess(request, response, authentication);

        SecurityContext securityContext = (SecurityContext) session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY
        );
        assertThat(response.getRedirectedUrl()).isEqualTo("/dashboard/publish");
        assertThat(request.getSession(false).getId()).isEqualTo(originalSessionId);
        assertThat(session.getAttribute(OAuthLoginRedirectSupport.SESSION_RETURN_TO_ATTRIBUTE)).isNull();
        assertThat(session.getAttribute("platformPrincipal")).isEqualTo(principal);
        assertThat(securityContext).isNotNull();
        assertThat(securityContext.getAuthentication().getPrincipal()).isEqualTo(principal);
    }

    @Test
    void successHandler_appliesSubPathPrefixExactlyOnce() throws Exception {
        OAuthLoginFlowService oauthLoginFlowService = mock(OAuthLoginFlowService.class);
        OAuth2LoginSuccessHandler handler = new OAuth2LoginSuccessHandler(
                new com.iflytek.skillhub.auth.session.PlatformSessionService(),
                oauthLoginFlowService
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        // X-Forwarded-Prefix is reflected into the context path under forward-headers-strategy=framework.
        request.setContextPath("/skillhub");
        MockHttpServletResponse response = new MockHttpServletResponse();
        HttpSession session = request.getSession(true);
        session.setAttribute(OAuthLoginRedirectSupport.SESSION_RETURN_TO_ATTRIBUTE, "/dashboard/publish");

        var principal = new com.iflytek.skillhub.auth.rbac.PlatformPrincipal(
                "user-1", "User", "user@example.com", null, "github", Set.of()
        );
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new DefaultOAuth2User(List.of(), Map.of("platformPrincipal", principal, "login", "user"), "login"),
                null,
                List.of()
        );
        org.mockito.Mockito.when(oauthLoginFlowService.consumeReturnTo(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    HttpSession currentSession = invocation.getArgument(0);
                    Object value = currentSession.getAttribute(OAuthLoginRedirectSupport.SESSION_RETURN_TO_ATTRIBUTE);
                    currentSession.removeAttribute(OAuthLoginRedirectSupport.SESSION_RETURN_TO_ATTRIBUTE);
                    return value;
                });

        handler.onAuthenticationSuccess(request, response, authentication);

        // Prefix applied exactly once by the redirect strategy — not doubled.
        assertThat(response.getRedirectedUrl()).isEqualTo("/skillhub/dashboard/publish");
    }

    /**
     * Regression test: when an unauthenticated client hits a protected API endpoint, Spring Security
     * caches that request. With {@code SavedRequestAwareAuthenticationSuccessHandler} the post-login
     * redirect would resolve to the cached API URL, leaving the user staring at raw JSON instead of
     * the dashboard. The handler must ignore the saved request and fall back to the default target.
     */
    @Test
    void successHandler_ignoresSavedApiRequestAndRedirectsToDefault() throws Exception {
        OAuthLoginFlowService oauthLoginFlowService = mock(OAuthLoginFlowService.class);
        OAuth2LoginSuccessHandler handler = new OAuth2LoginSuccessHandler(
                new com.iflytek.skillhub.auth.session.PlatformSessionService(),
                oauthLoginFlowService
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI("/api/web/skills");
        MockHttpServletResponse response = new MockHttpServletResponse();
        // Simulate Spring Security saving the API request that triggered login.
        new HttpSessionRequestCache().saveRequest(request, response);

        var principal = new com.iflytek.skillhub.auth.rbac.PlatformPrincipal(
                "user-1", "User", "user@example.com", null, "github", Set.of()
        );
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new DefaultOAuth2User(List.of(), Map.of("platformPrincipal", principal, "login", "user"), "login"),
                null,
                List.of()
        );
        org.mockito.Mockito.when(oauthLoginFlowService.consumeReturnTo(org.mockito.ArgumentMatchers.any()))
                .thenReturn(null);

        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getRedirectedUrl()).isEqualTo("/dashboard");
    }

    @Test
    void failureHandler_redirectsBackToLoginWithReturnTo() throws Exception {
        OAuthLoginFlowService oauthLoginFlowService = mock(OAuthLoginFlowService.class);
        OAuth2LoginFailureHandler handler = new OAuth2LoginFailureHandler(oauthLoginFlowService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        HttpSession session = request.getSession(true);
        session.setAttribute(OAuthLoginRedirectSupport.SESSION_RETURN_TO_ATTRIBUTE, "/settings/accounts");
        org.mockito.Mockito.when(oauthLoginFlowService.consumeReturnTo(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    HttpSession currentSession = invocation.getArgument(0);
                    Object value = currentSession.getAttribute(OAuthLoginRedirectSupport.SESSION_RETURN_TO_ATTRIBUTE);
                    currentSession.removeAttribute(OAuthLoginRedirectSupport.SESSION_RETURN_TO_ATTRIBUTE);
                    return value;
                });
        org.mockito.Mockito.when(oauthLoginFlowService.resolveFailureRedirect(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.eq("/settings/accounts")))
                .thenReturn("/login?returnTo=%2Fsettings%2Faccounts");

        handler.onAuthenticationFailure(
                request,
                response,
                new OAuth2AuthenticationException(new OAuth2Error("invalid_request"))
        );

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?returnTo=%2Fsettings%2Faccounts");
        assertThat(session.getAttribute(OAuthLoginRedirectSupport.SESSION_RETURN_TO_ATTRIBUTE)).isNull();
    }
}
