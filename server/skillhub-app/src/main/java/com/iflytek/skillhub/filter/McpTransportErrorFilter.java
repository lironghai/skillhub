package com.iflytek.skillhub.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Normalizes exceptions thrown by the MCP Streamable HTTP transport before the
 * servlet error pipeline can serialize Java exception internals to external clients.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class McpTransportErrorFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(McpTransportErrorFilter.class);

    private final ApiResponseFactory apiResponseFactory;
    private final ObjectMapper objectMapper;

    public McpTransportErrorFilter(ApiResponseFactory apiResponseFactory, ObjectMapper objectMapper) {
        this.apiResponseFactory = apiResponseFactory;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!isMcpStreamableRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            filterChain.doFilter(request, response);
        } catch (Exception ex) {
            if (response.isCommitted()) {
                throw ex;
            }
            renderMcpError(request, response, ex);
        }
    }

    private boolean isMcpStreamableRequest(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path != null && path.equals("/api/mcp");
    }

    private void renderMcpError(HttpServletRequest request,
                                HttpServletResponse response,
                                Exception ex) throws IOException {
        if (isSessionNotFound(ex)) {
            log.info("MCP session not found [method={}, path={}]", request.getMethod(), request.getRequestURI());
            writeError(response, HttpStatus.NOT_FOUND, "MCP session not found");
            return;
        }

        log.warn("MCP transport error [method={}, path={}]", request.getMethod(), request.getRequestURI(), ex);
        writeError(response, HttpStatus.INTERNAL_SERVER_ERROR, "MCP transport error");
    }

    private boolean isSessionNotFound(Exception ex) {
        String message = ex.getMessage();
        return message != null && message.startsWith("Session not found:");
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), apiResponseFactory.errorMessage(status.value(), message));
    }
}
