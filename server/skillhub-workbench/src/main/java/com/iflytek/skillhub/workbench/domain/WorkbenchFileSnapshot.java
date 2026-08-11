package com.iflytek.skillhub.workbench.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "workbench_file_snapshot")
public class WorkbenchFileSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "snapshot_type", nullable = false, length = 32)
    private WorkbenchFileSnapshotType snapshotType;

    @Column(name = "file_path", nullable = false, length = 512)
    private String filePath;

    @Column(name = "content_storage_key", nullable = false, length = 512)
    private String contentStorageKey;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "content_type", length = 128)
    private String contentType;

    @Column(name = "retention_until")
    private Instant retentionUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected WorkbenchFileSnapshot() {
    }

    public WorkbenchFileSnapshot(Long sessionId, WorkbenchFileSnapshotType snapshotType, String filePath,
                                 String contentStorageKey, String sha256, Long sizeBytes,
                                 String contentType, Instant retentionUntil, Clock clock) {
        this.sessionId = WorkbenchSession.requirePositive(sessionId, "sessionId");
        this.snapshotType = Objects.requireNonNull(snapshotType, "snapshotType");
        this.filePath = requireRelativeFilePath(filePath);
        this.contentStorageKey = WorkbenchSession.requireText(contentStorageKey, "contentStorageKey");
        this.sha256 = requireSha256(sha256);
        this.sizeBytes = requireNonNegative(sizeBytes, "sizeBytes");
        this.contentType = contentType;
        this.retentionUntil = retentionUntil;
        this.createdAt = Instant.now(Objects.requireNonNull(clock, "clock"));
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now(Clock.systemUTC());
        }
    }

    static String requireRelativeFilePath(String filePath) {
        return normalizeFilePath(filePath);
    }

    public static String normalizeFilePath(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("filePath must be a normalized relative workspace path");
        }
        String value = filePath.replace('\\', '/');
        if (value.startsWith("/") || value.contains(":")) {
            throw new IllegalArgumentException("filePath must be a normalized relative workspace path");
        }
        for (String segment : value.split("/", -1)) {
            if (segment.isBlank() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("filePath must be a normalized relative workspace path");
            }
        }
        return value;
    }

    private static String requireSha256(String sha256) {
        String value = WorkbenchSession.requireText(sha256, "sha256");
        if (value.length() != 64) {
            throw new IllegalArgumentException("sha256 must be 64 characters");
        }
        return value;
    }

    private static Long requireNonNegative(Long value, String name) {
        if (value == null || value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
        return value;
    }

    public Long getId() {
        return id;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public WorkbenchFileSnapshotType getSnapshotType() {
        return snapshotType;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getContentStorageKey() {
        return contentStorageKey;
    }

    public String getSha256() {
        return sha256;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public String getContentType() {
        return contentType;
    }

    public Instant getRetentionUntil() {
        return retentionUntil;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
