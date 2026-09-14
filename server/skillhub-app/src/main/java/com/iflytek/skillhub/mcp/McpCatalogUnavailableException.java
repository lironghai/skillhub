package com.iflytek.skillhub.mcp;

public class McpCatalogUnavailableException extends RuntimeException {
    public McpCatalogUnavailableException(String message) {
        super(message);
    }

    public McpCatalogUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
