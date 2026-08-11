package com.iflytek.skillhub.workbench.poc;

import java.nio.file.Path;

public record SessionWorkspace(String userId, String sessionId, Path root) {}
