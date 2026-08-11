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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "workbench_session_event")
public class WorkbenchSessionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private WorkbenchSessionEventType eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
    private String payloadJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected WorkbenchSessionEvent() {
    }

    public WorkbenchSessionEvent(Long sessionId, WorkbenchSessionEventType eventType,
                                 String payloadJson, Clock clock) {
        this.sessionId = WorkbenchSession.requirePositive(sessionId, "sessionId");
        this.eventType = Objects.requireNonNull(eventType, "eventType");
        this.payloadJson = WorkbenchJsonValue.objectOrDefault(payloadJson, "payloadJson");
        this.createdAt = Instant.now(Objects.requireNonNull(clock, "clock"));
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now(Clock.systemUTC());
        }
        if (payloadJson == null) {
            payloadJson = "{}";
        }
    }

    public Long getId() {
        return id;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public WorkbenchSessionEventType getEventType() {
        return eventType;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
