package com.iflytek.skillhub.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class McpTransportErrorFilterTest {

    private final McpTransportErrorFilter filter = new McpTransportErrorFilter(
            new ApiResponseFactory(
                    new StaticMessageSource(),
                    Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
                    new RequestIdAccessor()),
            new ObjectMapper().registerModule(new JavaTimeModule()));

    @Test
    void doFilterInternal_sanitizesMcpSessionExceptions() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/mcp");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (servletRequest, servletResponse) -> {
            throw new IllegalStateException("Session not found: missing");
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getContentAsString()).contains("\"code\":404");
        assertThat(response.getContentAsString()).contains("\"msg\":\"MCP session not found\"");
        assertThat(response.getContentAsString()).doesNotContain("stackTrace");
        assertThat(response.getContentAsString()).doesNotContain("org.springframework.ai");
    }

    @Test
    void doFilterInternal_doesNotWrapStreamableMcpEndpoint() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/mcp");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<ServletResponse> responseSeenByChain = new AtomicReference<>();

        FilterChain chain = (servletRequest, servletResponse) -> {
            responseSeenByChain.set(servletResponse);
            servletResponse.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
            servletResponse.getWriter().write("event:message\n");
        };

        filter.doFilter(request, response, chain);

        assertThat(responseSeenByChain.get()).isSameAs(response);
        assertThat(response.getContentType()).isEqualTo(MediaType.TEXT_EVENT_STREAM_VALUE);
        assertThat(response.getContentAsString()).isEqualTo("event:message\n");
    }
}
