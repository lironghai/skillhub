package com.iflytek.skillhub.workbench.port;

import com.iflytek.skillhub.workbench.domain.WorkbenchSession;
import java.util.Objects;

public record AgentRuntimeEvent(
        AgentRuntimeEventType type,
        String payloadJson,
        String filePath,
        byte[] fileContent,
        String contentType) {

    public AgentRuntimeEvent {
        Objects.requireNonNull(type, "type");
        payloadJson = objectOrDefault(payloadJson, "payloadJson");
        if ((type == AgentRuntimeEventType.FILE_WRITTEN || type == AgentRuntimeEventType.FILE_DELETED)
                && (filePath == null || filePath.isBlank())) {
            throw new IllegalArgumentException("filePath must not be blank for file runtime events");
        }
        if (type == AgentRuntimeEventType.FILE_WRITTEN) {
            Objects.requireNonNull(fileContent, "fileContent");
            fileContent = fileContent.clone();
        }
    }

    @Override
    public byte[] fileContent() {
        return fileContent == null ? null : fileContent.clone();
    }

    public static AgentRuntimeEvent modelMessage(String message) {
        WorkbenchSession.requireText(message, "message");
        return new AgentRuntimeEvent(AgentRuntimeEventType.MODEL_MESSAGE,
                "{\"message\":\"" + escape(message) + "\"}", null, null, null);
    }

    public static AgentRuntimeEvent fileWritten(String filePath, byte[] content, String contentType) {
        return new AgentRuntimeEvent(AgentRuntimeEventType.FILE_WRITTEN,
                "{\"action\":\"file.written\",\"path\":\"" + escape(filePath) + "\"}",
                filePath, content, contentType);
    }

    public static AgentRuntimeEvent fileDeleted(String filePath) {
        return new AgentRuntimeEvent(AgentRuntimeEventType.FILE_DELETED,
                "{\"action\":\"file.deleted\",\"path\":\"" + escape(filePath) + "\"}",
                filePath, null, null);
    }

    public static AgentRuntimeEvent error(String message) {
        WorkbenchSession.requireText(message, "message");
        return new AgentRuntimeEvent(AgentRuntimeEventType.ERROR,
                "{\"message\":\"" + escape(message) + "\"}", null, null, null);
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (ch == '\\') {
                builder.append("\\\\");
            } else if (ch == '"') {
                builder.append("\\\"");
            } else if (ch == '\n') {
                builder.append("\\n");
            } else if (ch == '\r') {
                builder.append("\\r");
            } else if (ch == '\t') {
                builder.append("\\t");
            } else if (ch < 0x20) {
                builder.append(String.format("\\u%04x", (int) ch));
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private static String objectOrDefault(String value, String name) {
        if (value == null || value.isBlank()) {
            return "{}";
        }
        String trimmed = value.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            throw new IllegalArgumentException(name + " must be a JSON object");
        }
        return value;
    }
}
