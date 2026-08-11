package com.iflytek.skillhub.domain.skillbundle;

import com.iflytek.skillhub.domain.skill.SkillVisibility;
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
import java.time.Clock;
import java.time.Instant;

@Entity
@Table(name = "skill_bundle")
public class SkillBundle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "namespace_id", nullable = false)
    private Long namespaceId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 128)
    private String slug;

    @Column(length = 512)
    private String summary;

    @Column(name = "avatar_url", length = 2048)
    private String avatarUrl;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "role_description", columnDefinition = "TEXT")
    private String roleDescription;

    @Column(name = "applicable_scenarios", columnDefinition = "TEXT")
    private String applicableScenarios;

    @Column(columnDefinition = "TEXT")
    private String methodology;

    @Column(name = "recommended_skill_notes", columnDefinition = "TEXT")
    private String recommendedSkillNotes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SkillVisibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SkillBundleStatus status = SkillBundleStatus.DRAFT;

    @Column(name = "owner_id", nullable = false, length = 128)
    private String ownerId;

    @Column(name = "download_count", nullable = false)
    private long downloadCount;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_by", length = 128)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SkillBundle() {
    }

    public SkillBundle(Long namespaceId, String name, String slug, String ownerId, SkillVisibility visibility) {
        this.namespaceId = namespaceId;
        this.name = name;
        this.slug = slug;
        this.ownerId = ownerId;
        this.visibility = visibility;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now(Clock.systemUTC());
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now(Clock.systemUTC());
    }

    public Long getId() {
        return id;
    }

    public Long getNamespaceId() {
        return namespaceId;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public String getSummary() {
        return summary;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public String getDescription() {
        return description;
    }

    public String getRoleDescription() {
        return roleDescription;
    }

    public String getApplicableScenarios() {
        return applicableScenarios;
    }

    public String getMethodology() {
        return methodology;
    }

    public String getRecommendedSkillNotes() {
        return recommendedSkillNotes;
    }

    public SkillVisibility getVisibility() {
        return visibility;
    }

    public SkillBundleStatus getStatus() {
        return status;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public long getDownloadCount() {
        return downloadCount;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void updateBasicInfo(String name,
                                String slug,
                                String summary,
                                String avatarUrl,
                                String description,
                                String roleDescription,
                                String applicableScenarios,
                                String methodology,
                                String recommendedSkillNotes,
                                SkillVisibility visibility,
                                SkillBundleStatus status,
                                String operatorId) {
        this.name = name;
        this.slug = slug;
        this.summary = summary;
        this.avatarUrl = avatarUrl;
        this.description = description;
        this.roleDescription = roleDescription;
        this.applicableScenarios = applicableScenarios;
        this.methodology = methodology;
        this.recommendedSkillNotes = recommendedSkillNotes;
        this.visibility = visibility;
        this.status = status;
        this.updatedBy = operatorId;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setRoleDescription(String roleDescription) {
        this.roleDescription = roleDescription;
    }

    public void setApplicableScenarios(String applicableScenarios) {
        this.applicableScenarios = applicableScenarios;
    }

    public void setMethodology(String methodology) {
        this.methodology = methodology;
    }

    public void setRecommendedSkillNotes(String recommendedSkillNotes) {
        this.recommendedSkillNotes = recommendedSkillNotes;
    }

    public void setStatus(SkillBundleStatus status) {
        this.status = status;
    }

    public void incrementDownloadCount() {
        this.downloadCount += 1;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
