package com.iflytek.skillhub.workbench.domain;

public enum WorkbenchSessionEventType {
    AUDIT,
    USER_MESSAGE,
    MODEL_MESSAGE,
    FILE_CHANGED,
    TOOL_CALL,
    APPROVAL_REQUIRED,
    APPROVAL_DECIDED,
    STATUS_CHANGED,
    ERROR
}
