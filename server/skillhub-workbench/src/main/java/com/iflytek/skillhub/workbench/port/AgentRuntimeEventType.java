package com.iflytek.skillhub.workbench.port;

public enum AgentRuntimeEventType {
    MODEL_MESSAGE,
    FILE_WRITTEN,
    FILE_DELETED,
    TOOL_CALL,
    APPROVAL_REQUIRED,
    ERROR
}
