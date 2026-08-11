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
@Table(name = "skill_bundle_label")
public class SkillBundleLabel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bundle_id", nullable = false)
    private Long bundleId;

    @Column(name = "label_id", nullable = false)
    private Long labelId;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SkillBundleLabel() {
    }

    public SkillBundleLabel(Long bundleId, Long labelId, String createdBy) {
        this.bundleId = bundleId;
        this.labelId = labelId;
        this.createdBy = createdBy;
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

    public Long getLabelId() {
        return labelId;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SkillBundleLabel that)) {
            return false;
        }
        return Objects.equals(bundleId, that.bundleId)
                && Objects.equals(labelId, that.labelId)
                && Objects.equals(createdBy, that.createdBy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bundleId, labelId, createdBy);
    }
}
