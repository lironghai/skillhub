package com.iflytek.skillhub.workbench.poc;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;

public final class SessionWorkspaceFilePolicy {

    private final Path root;

    public SessionWorkspaceFilePolicy(Path root) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
    }

    public Path resolve(String relativePath) {
        Objects.requireNonNull(relativePath, "relativePath");
        Path requested = Path.of(relativePath);
        if (requested.isAbsolute()) {
            throw new IllegalArgumentException("Absolute paths are not allowed in a session workspace");
        }
        Path resolved = root.resolve(requested).toAbsolutePath().normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Path must stay inside the session workspace");
        }
        rejectSymbolicLinks(resolved);
        return resolved;
    }

    private void rejectSymbolicLinks(Path resolved) {
        if (Files.exists(root, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(root)) {
            throw new IllegalArgumentException("Symbolic links are not allowed in a session workspace");
        }
        Path current = root;
        Path relative = root.relativize(resolved);
        for (Path segment : relative) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) {
                throw new IllegalArgumentException("Symbolic links are not allowed in a session workspace");
            }
        }
    }

    public String read(String relativePath) {
        try {
            return Files.readString(resolve(relativePath));
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read workspace file", ex);
        }
    }

    public void write(String relativePath, String content) {
        Path resolved = resolve(relativePath);
        try {
            Path parent = resolved.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(resolved, content);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to write workspace file", ex);
        }
    }
}
