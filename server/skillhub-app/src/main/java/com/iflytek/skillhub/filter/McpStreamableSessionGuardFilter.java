package com.iflytek.skillhub.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.policy.RouteSecurityPolicyRegistry;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import io.modelcontextprotocol.server.transport.WebMvcStreamableServerTransportProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@ConditionalOnProperty(prefix = "skillhub.mcp", name = "enabled", havingValue = "true", matchIfMissing = true)
@Order(Ordered.LOWEST_PRECEDENCE - 20)
public class McpStreamableSessionGuardFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(McpStreamableSessionGuardFilter.class);
    private static final String MCP_SESSION_ID = "Mcp-Session-Id";

    private final ObjectProvider<WebMvcStreamableServerTransportProvider> transportProvider;
    private final ApiResponseFactory apiResponseFactory;
    private final ObjectMapper objectMapper;

    public McpStreamableSessionGuardFilter(ObjectProvider<WebMvcStreamableServerTransportProvider> transportProvider,
                                           ApiResponseFactory apiResponseFactory,
                                           ObjectMapper objectMapper) {
        this.transportProvider = transportProvider;
        this.apiResponseFactory = apiResponseFactory;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String sessionId = request.getHeader(MCP_SESSION_ID);
        if (!isMcpStreamableRequest(request) || sessionId == null || sessionId.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        WebMvcStreamableServerTransportProvider provider = transportProvider.getIfAvailable();
        if (provider == null || hasSession(provider, sessionId)) {
            filterChain.doFilter(request, response);
            return;
        }

        log.info("MCP session not found [method={}, path={}]", request.getMethod(), request.getRequestURI());
        response.setStatus(HttpStatus.NOT_FOUND.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                apiResponseFactory.errorMessage(HttpStatus.NOT_FOUND.value(), "MCP session not found"));
    }

    private boolean isMcpStreamableRequest(HttpServletRequest request) {
        return "/api/mcp".equals(RouteSecurityPolicyRegistry.requestPath(request));
    }

    private boolean hasSession(WebMvcStreamableServerTransportProvider provider, String sessionId) {
        try {
            Field sessionsField = WebMvcStreamableServerTransportProvider.class.getDeclaredField("sessions");
            sessionsField.setAccessible(true);
            Object sessions = sessionsField.get(provider);
            return sessions instanceof Map<?, ?> map && map.containsKey(sessionId);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            log.warn("Unable to inspect MCP Streamable HTTP sessions; delegating to transport", ex);
            return true;
        }
    }
}
