package com.iflytek.skillhub.workbench.poc;

import java.util.Map;

public record MockMcpToolRequest(
        String serverName,
        String toolName,
        ToolRisk risk,
        Map<String, Object> input) {}
