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
@Table(name = "workbench_tool_approval")
public class WorkbenchToolApproval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "tool_name", nullable = false, length = 256)
    private String toolName;

    @Column(name = "mcp_server_id", length = 128)
    private String mcpServerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 32)
    private WorkbenchToolRiskLevel riskLevel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "arguments_redacted_json", nullable = false, columnDefinition = "jsonb")
    private String argumentsRedactedJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WorkbenchToolApprovalStatus status;

    @Column(name = "decision_by", length = 128)
    private String decisionBy;

    @Column(name = "decision_at")
    private Instant decisionAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected WorkbenchToolApproval() {
    }

    public WorkbenchToolApproval(Long sessionId, Long eventId, String toolName, String mcpServerId,
                                 WorkbenchToolRiskLevel riskLevel, String argumentsRedactedJson,
                                 Clock clock) {
        this.sessionId = WorkbenchSession.requirePositive(sessionId, "sessionId");
        this.eventId = WorkbenchSession.requirePositive(eventId, "eventId");
        this.toolName = WorkbenchSession.requireText(toolName, "toolName");
        this.mcpServerId = mcpServerId;
        this.riskLevel = Objects.requireNonNull(riskLevel, "riskLevel");
        this.argumentsRedactedJson = WorkbenchJsonValue.objectOrDefault(argumentsRedactedJson, "argumentsRedactedJson");
        this.status = riskLevel == WorkbenchToolRiskLevel.DENIED
                ? WorkbenchToolApprovalStatus.REJECTED
                : WorkbenchToolApprovalStatus.PENDING;
        this.createdAt = Instant.now(Objects.requireNonNull(clock, "clock"));
        if (this.status == WorkbenchToolApprovalStatus.REJECTED) {
            this.decisionAt = this.createdAt;
        }
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now(Clock.systemUTC());
        }
        if (argumentsRedactedJson == null) {
            argumentsRedactedJson = "{}";
        }
    }

    public void approve(String decisionBy, Clock clock) {
        if (riskLevel == WorkbenchToolRiskLevel.DENIED) {
            throw new IllegalStateException("denied tool approvals cannot be approved");
        }
        decide(WorkbenchToolApprovalStatus.APPROVED, decisionBy, clock);
    }

    public void reject(String decisionBy, Clock clock) {
        decide(WorkbenchToolApprovalStatus.REJECTED, decisionBy, clock);
    }

    public void expire(Clock clock) {
        decide(WorkbenchToolApprovalStatus.EXPIRED, null, clock);
    }

    private void decide(WorkbenchToolApprovalStatus targetStatus, String userId, Clock clock) {
        if (status != WorkbenchToolApprovalStatus.PENDING) {
            throw new IllegalStateException("Tool approval is already decided");
        }
        if (targetStatus != WorkbenchToolApprovalStatus.EXPIRED) {
            decisionBy = WorkbenchSession.requireText(userId, "decisionBy");
        }
        status = targetStatus;
        decisionAt = Instant.now(Objects.requireNonNull(clock, "clock"));
    }

    public Long getId() {
        return id;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public Long getEventId() {
        return eventId;
    }

    public String getToolName() {
        return toolName;
    }

    public String getMcpServerId() {
        return mcpServerId;
    }

    public WorkbenchToolRiskLevel getRiskLevel() {
        return riskLevel;
    }

    public String getArgumentsRedactedJson() {
        return argumentsRedactedJson;
    }

    public WorkbenchToolApprovalStatus getStatus() {
        return status;
    }

    public String getDecisionBy() {
        return decisionBy;
    }

    public Instant getDecisionAt() {
        return decisionAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
