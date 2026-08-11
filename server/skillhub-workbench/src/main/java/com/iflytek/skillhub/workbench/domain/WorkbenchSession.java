package com.iflytek.skillhub.workbench.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "workbench_session")
public class WorkbenchSession {

    private static final Set<WorkbenchSessionStatus> TERMINAL_STATUSES = EnumSet.of(
            WorkbenchSessionStatus.PUBLISHED,
            WorkbenchSessionStatus.FAILED,
            WorkbenchSessionStatus.CANCELLED,
            WorkbenchSessionStatus.EXPIRED
    );

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;

    @Column(name = "namespace_id", nullable = false)
    private Long namespaceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WorkbenchMode mode;

    @Column(name = "source_skill_id")
    private Long sourceSkillId;

    @Column(name = "source_version_id")
    private Long sourceVersionId;

    @Column(name = "target_slug", nullable = false, length = 128)
    private String targetSlug;

    @Column(name = "target_version", nullable = false, length = 64)
    private String targetVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "runtime_provider", nullable = false, length = 64)
    private WorkbenchRuntimeProvider runtimeProvider;

    @Enumerated(EnumType.STRING)
    @Column(name = "isolation_scope", nullable = false, length = 32)
    private WorkbenchIsolationScope isolationScope;

    @Column(name = "workspace_key", nullable = false, length = 512, unique = true)
    private String workspaceKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WorkbenchSessionStatus status;

    @Version
    @Column(nullable = false)
    private Integer version = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected WorkbenchSession() {
    }

    public static WorkbenchSession createSkill(String userId, Long namespaceId, String targetSlug,
                                               String targetVersion, String workspaceKey,
                                               Instant expiresAt, Clock clock) {
        return new WorkbenchSession(userId, namespaceId, WorkbenchMode.CREATE_SKILL, null, null,
                targetSlug, targetVersion, workspaceKey, expiresAt, clock);
    }

    public static WorkbenchSession updateSkill(String userId, Long namespaceId, Long sourceSkillId,
                                               Long sourceVersionId, String targetSlug,
                                               String targetVersion, String workspaceKey,
                                               Instant expiresAt, Clock clock) {
        return updateSkill(userId, namespaceId, new WorkbenchSourceVersion(sourceSkillId, sourceVersionId),
                targetSlug, targetVersion, workspaceKey, expiresAt, clock);
    }

    public static WorkbenchSession updateSkill(String userId, Long namespaceId, WorkbenchSourceVersion sourceVersion,
                                               String targetSlug, String targetVersion, String workspaceKey,
                                               Instant expiresAt, Clock clock) {
        Objects.requireNonNull(sourceVersion, "sourceVersion");
        return new WorkbenchSession(userId, namespaceId, WorkbenchMode.UPDATE_SKILL,
                sourceVersion.skillId(),
                sourceVersion.versionId(),
                targetSlug, targetVersion, workspaceKey, expiresAt, clock);
    }

    private WorkbenchSession(String userId, Long namespaceId, WorkbenchMode mode, Long sourceSkillId,
                             Long sourceVersionId, String targetSlug, String targetVersion,
                             String workspaceKey, Instant expiresAt, Clock clock) {
        this.userId = requireText(userId, "userId");
        this.namespaceId = requirePositive(namespaceId, "namespaceId");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.sourceSkillId = sourceSkillId;
        this.sourceVersionId = sourceVersionId;
        this.targetSlug = requireText(targetSlug, "targetSlug");
        this.targetVersion = requireText(targetVersion, "targetVersion");
        this.runtimeProvider = WorkbenchRuntimeProvider.AGENTSCOPE_JAVA;
        this.isolationScope = WorkbenchIsolationScope.SESSION;
        this.workspaceKey = requireText(workspaceKey, "workspaceKey");
        this.status = WorkbenchSessionStatus.DRAFT;
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.createdAt = now(clock);
        this.updatedAt = this.createdAt;
        validateModeSources();
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now(Clock.systemUTC());
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now(Clock.systemUTC());
    }

    public void start(Clock clock) {
        transitionTo(WorkbenchSessionStatus.RUNNING, clock, WorkbenchSessionStatus.DRAFT);
    }

    public void waitForApproval(Clock clock) {
        transitionTo(WorkbenchSessionStatus.WAITING_APPROVAL, clock, WorkbenchSessionStatus.RUNNING);
    }

    public void resumeFromApproval(Clock clock) {
        transitionTo(WorkbenchSessionStatus.RUNNING, clock, WorkbenchSessionStatus.WAITING_APPROVAL);
    }

    public void markReadyForReview(Clock clock) {
        transitionTo(WorkbenchSessionStatus.READY_FOR_REVIEW, clock,
                WorkbenchSessionStatus.DRAFT,
                WorkbenchSessionStatus.RUNNING);
    }

    public void startPublishing(Clock clock) {
        transitionTo(WorkbenchSessionStatus.PUBLISHING, clock, WorkbenchSessionStatus.READY_FOR_REVIEW);
    }

    public void markPublished(Clock clock) {
        transitionTo(WorkbenchSessionStatus.PUBLISHED, clock, WorkbenchSessionStatus.PUBLISHING);
    }

    public void fail(Clock clock) {
        transitionTo(WorkbenchSessionStatus.FAILED, clock, nonTerminalStatuses());
    }

    public void cancel(Clock clock) {
        transitionTo(WorkbenchSessionStatus.CANCELLED, clock, nonTerminalStatuses());
    }

    public void expire(Clock clock) {
        transitionTo(WorkbenchSessionStatus.EXPIRED, clock, nonTerminalStatuses());
    }

    public boolean isTerminal() {
        return TERMINAL_STATUSES.contains(status);
    }

    private void transitionTo(WorkbenchSessionStatus target, Clock clock,
                              WorkbenchSessionStatus... allowedSources) {
        transitionTo(target, clock, EnumSet.copyOf(Set.of(allowedSources)));
    }

    private void transitionTo(WorkbenchSessionStatus target, Clock clock,
                              Set<WorkbenchSessionStatus> allowedSources) {
        if (!allowedSources.contains(status)) {
            throw new IllegalStateException("Cannot transition workbench session from "
                    + status + " to " + target);
        }
        status = target;
        updatedAt = now(clock);
    }

    private static Set<WorkbenchSessionStatus> nonTerminalStatuses() {
        EnumSet<WorkbenchSessionStatus> statuses = EnumSet.allOf(WorkbenchSessionStatus.class);
        statuses.removeAll(TERMINAL_STATUSES);
        return statuses;
    }

    private void validateModeSources() {
        if (mode == WorkbenchMode.CREATE_SKILL && (sourceSkillId != null || sourceVersionId != null)) {
            throw new IllegalArgumentException("Create sessions must not include source skill or version ids");
        }
        if (mode == WorkbenchMode.UPDATE_SKILL && (sourceSkillId == null || sourceVersionId == null)) {
            throw new IllegalArgumentException("Update sessions require source skill and version ids");
        }
    }

    private static Instant now(Clock clock) {
        return Instant.now(Objects.requireNonNull(clock, "clock"));
    }

    public static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    public static Long requirePositive(Long value, String name) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    public Long getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public Long getNamespaceId() {
        return namespaceId;
    }

    public WorkbenchMode getMode() {
        return mode;
    }

    public Long getSourceSkillId() {
        return sourceSkillId;
    }

    public Long getSourceVersionId() {
        return sourceVersionId;
    }

    public String getTargetSlug() {
        return targetSlug;
    }

    public String getTargetVersion() {
        return targetVersion;
    }

    public WorkbenchRuntimeProvider getRuntimeProvider() {
        return runtimeProvider;
    }

    public WorkbenchIsolationScope getIsolationScope() {
        return isolationScope;
    }

    public String getWorkspaceKey() {
        return workspaceKey;
    }

    public WorkbenchSessionStatus getStatus() {
        return status;
    }

    public Integer getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
