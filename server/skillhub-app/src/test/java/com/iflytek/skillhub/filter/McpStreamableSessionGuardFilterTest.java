package com.iflytek.skillhub.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.transport.WebMvcStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpStreamableServerSession;
import jakarta.servlet.FilterChain;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

class McpStreamableSessionGuardFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final ApiResponseFactory apiResponseFactory =
            new ApiResponseFactory(
                    new StaticMessageSource(),
                    Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
                    new RequestIdAccessor());

    @Test
    void doFilterInternal_sanitizesMissingStreamableSession() throws Exception {
        WebMvcStreamableServerTransportProvider provider = streamableProvider();
        McpStreamableSessionGuardFilter filter = filter(provider);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/skillhub/api/mcp");
        request.setContextPath("/skillhub");
        request.setServletPath("/api/mcp");
        request.addHeader("Mcp-Session-Id", "missing-session");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        filter.doFilter(request, response, (servletRequest, servletResponse) -> chainInvoked.set(true));

        assertThat(chainInvoked).isFalse();
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getContentAsString()).contains("\"msg\":\"MCP session not found\"");
        assertThat(response.getContentAsString()).doesNotContain("stackTrace");
    }

    @Test
    void doFilterInternal_allowsExistingStreamableSessionWithoutWrappingResponse() throws Exception {
        WebMvcStreamableServerTransportProvider provider = streamableProvider();
        putSession(provider, "existing-session");
        McpStreamableSessionGuardFilter filter = filter(provider);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/mcp");
        request.addHeader("Mcp-Session-Id", "existing-session");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
            servletResponse.getWriter().write("event:message\n");
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).isEqualTo(MediaType.TEXT_EVENT_STREAM_VALUE);
        assertThat(response.getContentAsString()).isEqualTo("event:message\n");
    }

    @Test
    void doFilterInternal_allowsInitializeRequestWithoutSessionHeader() throws Exception {
        McpStreamableSessionGuardFilter filter = filter(streamableProvider());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/mcp");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        filter.doFilter(request, response, (servletRequest, servletResponse) -> chainInvoked.set(true));

        assertThat(chainInvoked).isTrue();
    }

    private McpStreamableSessionGuardFilter filter(WebMvcStreamableServerTransportProvider provider) {
        @SuppressWarnings("unchecked")
        ObjectProvider<WebMvcStreamableServerTransportProvider> providerFactory = mock(ObjectProvider.class);
        when(providerFactory.getIfAvailable()).thenReturn(provider);
        return new McpStreamableSessionGuardFilter(providerFactory, apiResponseFactory, objectMapper);
    }

    private WebMvcStreamableServerTransportProvider streamableProvider() {
        return WebMvcStreamableServerTransportProvider.builder()
                .jsonMapper(new JacksonMcpJsonMapper(objectMapper))
                .mcpEndpoint("/api/mcp")
                .build();
    }

    @SuppressWarnings("unchecked")
    private void putSession(WebMvcStreamableServerTransportProvider provider, String sessionId) {
        Map<String, McpStreamableServerSession> sessions =
                (Map<String, McpStreamableServerSession>) ReflectionTestUtils.getField(provider, "sessions");
        sessions.put(sessionId, mock(McpStreamableServerSession.class));
    }
}
