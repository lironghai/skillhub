package com.iflytek.skillhub.domain.skillbundle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "skill_bundle_item")
public class SkillBundleItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bundle_id", nullable = false)
    private Long bundleId;

    @Column(name = "skill_id", nullable = false)
    private Long skillId;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SkillBundleItem() {
    }

    public SkillBundleItem(Long bundleId, Long skillId, Integer sortOrder, String note) {
        this.bundleId = bundleId;
        this.skillId = skillId;
        this.sortOrder = sortOrder;
        this.note = note;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now(Clock.systemUTC());
    }

    public Long getId() {
        return id;
    }

    public Long getBundleId() {
        return bundleId;
    }

    public Long getSkillId() {
        return skillId;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SkillBundleItem that)) {
            return false;
        }
        return Objects.equals(bundleId, that.bundleId)
                && Objects.equals(skillId, that.skillId)
                && Objects.equals(sortOrder, that.sortOrder)
                && Objects.equals(note, that.note);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bundleId, skillId, sortOrder, note);
    }
}
