package com.iflytek.skillhub.workbench.domain;

import com.iflytek.skillhub.domain.skill.SkillVisibility;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "workbench_publish_candidate")
public class WorkbenchPublishCandidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "package_fingerprint", nullable = false, length = 128)
    private String packageFingerprint;

    @Column(name = "file_count", nullable = false)
    private Integer fileCount;

    @Column(name = "total_size", nullable = false)
    private Long totalSize;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_status", nullable = false, length = 32)
    private WorkbenchPublishValidationStatus validationStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 32)
    private SkillVisibility visibility;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_report_json", nullable = false, columnDefinition = "jsonb")
    private String validationReportJson;

    @Column(name = "skill_version_id")
    private Long skillVersionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected WorkbenchPublishCandidate() {
    }

    public WorkbenchPublishCandidate(Long sessionId, String packageFingerprint, Integer fileCount,
                                     Long totalSize, SkillVisibility visibility,
                                     String validationReportJson, Clock clock) {
        this.sessionId = WorkbenchSession.requirePositive(sessionId, "sessionId");
        this.packageFingerprint = WorkbenchSession.requireText(packageFingerprint, "packageFingerprint");
        this.fileCount = requireNonNegative(fileCount, "fileCount");
        this.totalSize = requireNonNegative(totalSize, "totalSize");
        this.visibility = Objects.requireNonNullElse(visibility, SkillVisibility.PRIVATE);
        this.validationStatus = WorkbenchPublishValidationStatus.PENDING;
        this.validationReportJson = WorkbenchJsonValue.objectOrDefault(validationReportJson, "validationReportJson");
        this.createdAt = Instant.now(Objects.requireNonNull(clock, "clock"));
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now(Clock.systemUTC());
        }
        if (validationReportJson == null) {
            validationReportJson = "{}";
        }
        if (visibility == null) {
            visibility = SkillVisibility.PRIVATE;
        }
    }

    public void markValidationPassed(String validationReportJson) {
        requireNotPublished();
        validationStatus = WorkbenchPublishValidationStatus.PASSED;
        this.validationReportJson = WorkbenchJsonValue.objectOrDefault(validationReportJson, "validationReportJson");
    }

    public void markValidationFailed(String validationReportJson) {
        requireNotPublished();
        validationStatus = WorkbenchPublishValidationStatus.FAILED;
        this.validationReportJson = WorkbenchJsonValue.objectOrDefault(validationReportJson, "validationReportJson");
    }

    public void markPublished(Long skillVersionId) {
        requireNotPublished();
        if (validationStatus != WorkbenchPublishValidationStatus.PASSED) {
            throw new IllegalStateException("Only validated workbench candidates can be published");
        }
        this.skillVersionId = WorkbenchSession.requirePositive(skillVersionId, "skillVersionId");
        validationStatus = WorkbenchPublishValidationStatus.PUBLISHED;
    }

    private void requireNotPublished() {
        if (validationStatus == WorkbenchPublishValidationStatus.PUBLISHED) {
            throw new IllegalStateException("published workbench candidates are terminal");
        }
    }

    private static Integer requireNonNegative(Integer value, String name) {
        if (value == null || value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
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

    public String getPackageFingerprint() {
        return packageFingerprint;
    }

    public Integer getFileCount() {
        return fileCount;
    }

    public Long getTotalSize() {
        return totalSize;
    }

    public WorkbenchPublishValidationStatus getValidationStatus() {
        return validationStatus;
    }

    public SkillVisibility getVisibility() {
        return visibility;
    }

    public String getValidationReportJson() {
        return validationReportJson;
    }

    public Long getSkillVersionId() {
        return skillVersionId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
