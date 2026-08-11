package com.iflytek.skillhub.workbench.poc;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public final class SessionWorkspaceResolver {

    private final Path baseRoot;

    public SessionWorkspaceResolver(Path baseRoot) {
        this.baseRoot = Objects.requireNonNull(baseRoot, "baseRoot").toAbsolutePath().normalize();
    }

    public SessionWorkspace resolve(String userId, String sessionId) {
        requireText(userId, "userId");
        requireText(sessionId, "sessionId");
        Path sessionRoot = baseRoot.resolve("users")
                .resolve(stableSegment(userId))
                .resolve("sessions")
                .resolve(stableSegment(sessionId))
                .normalize();
        if (!sessionRoot.startsWith(baseRoot)) {
            throw new IllegalArgumentException("Resolved workspace escaped base root");
        }
        try {
            Files.createDirectories(sessionRoot);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to create session workspace", ex);
        }
        return new SessionWorkspace(userId, sessionId, sessionRoot);
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
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private static void requireText(String value, String name) {
        if (Objects.requireNonNull(value, name).isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
