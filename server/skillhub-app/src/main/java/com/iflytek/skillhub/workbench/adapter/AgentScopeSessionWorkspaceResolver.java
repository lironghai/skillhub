package com.iflytek.skillhub.workbench.adapter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

final class AgentScopeSessionWorkspaceResolver {

    private final Path baseRoot;

    AgentScopeSessionWorkspaceResolver(Path baseRoot) {
        this.baseRoot = Objects.requireNonNull(baseRoot, "baseRoot").toAbsolutePath().normalize();
    }

    Path resolve(String userId, String sessionId) {
        requireText(userId, "userId");
        requireText(sessionId, "sessionId");
        Path sessionRoot = baseRoot.resolve("users")
                .resolve(stableSegment(userId))
                .resolve("sessions")
                .resolve(stableSegment(sessionId))
                .normalize();
        if (!sessionRoot.startsWith(baseRoot)) {
            throw new IllegalArgumentException("Resolved runtime workspace escaped base root");
        }
        try {
            Files.createDirectories(sessionRoot);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to create runtime workspace", ex);
        }
        return sessionRoot;
    }

    private static String stableSegment(String value) {
        String readable = value.replaceAll("[^A-Za-z0-9._-]", "_");
        if (readable.length() > 32) {
            readable = readable.substring(0, 32);
        }
        return readable + "-" + sha256(value).substring(0, 12);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static void requireText(String value, String name) {
        if (Objects.requireNonNull(value, name).isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
