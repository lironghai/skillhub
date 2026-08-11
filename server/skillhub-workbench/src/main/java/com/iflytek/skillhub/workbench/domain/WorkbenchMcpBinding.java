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
@Table(name = "workbench_mcp_binding")
public class WorkbenchMcpBinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "mcp_server_id", nullable = false, length = 128)
    private String mcpServerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "catalog_source", nullable = false, length = 32)
    private WorkbenchMcpCatalogSource catalogSource;

    @Column(name = "runtime_endpoint_ref", nullable = false, length = 512)
    private String runtimeEndpointRef;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "enabled_tools_json", nullable = false, columnDefinition = "jsonb")
    private String enabledToolsJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "disabled_tools_json", nullable = false, columnDefinition = "jsonb")
    private String disabledToolsJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tool_policy_json", nullable = false, columnDefinition = "jsonb")
    private String toolPolicyJson;

    @Column(name = "policy_version", nullable = false, length = 64)
    private String policyVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WorkbenchMcpBindingStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected WorkbenchMcpBinding() {
    }

    public WorkbenchMcpBinding(Long sessionId, String mcpServerId, WorkbenchMcpCatalogSource catalogSource,
                               String runtimeEndpointRef, String enabledToolsJson, String disabledToolsJson,
                               String toolPolicyJson, String policyVersion, Clock clock) {
        this.sessionId = WorkbenchSession.requirePositive(sessionId, "sessionId");
        this.mcpServerId = WorkbenchSession.requireText(mcpServerId, "mcpServerId");
        this.catalogSource = Objects.requireNonNull(catalogSource, "catalogSource");
        this.runtimeEndpointRef = WorkbenchSession.requireText(runtimeEndpointRef, "runtimeEndpointRef");
        this.enabledToolsJson = WorkbenchJsonValue.arrayOrDefault(enabledToolsJson, "enabledToolsJson");
        this.disabledToolsJson = WorkbenchJsonValue.arrayOrDefault(disabledToolsJson, "disabledToolsJson");
        this.toolPolicyJson = WorkbenchJsonValue.objectOrDefault(toolPolicyJson, "toolPolicyJson");
        this.policyVersion = WorkbenchSession.requireText(policyVersion, "policyVersion");
        this.status = WorkbenchMcpBindingStatus.ENABLED;
        this.createdAt = Instant.now(Objects.requireNonNull(clock, "clock"));
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now(Clock.systemUTC());
        }
        if (enabledToolsJson == null) {
            enabledToolsJson = "[]";
        }
        if (disabledToolsJson == null) {
            disabledToolsJson = "[]";
        }
        if (toolPolicyJson == null) {
            toolPolicyJson = "{}";
        }
    }

    public void disable() {
        status = WorkbenchMcpBindingStatus.DISABLED;
    }

    public void enable() {
        status = WorkbenchMcpBindingStatus.ENABLED;
    }

    public void updateToolSelection(String enabledToolsJson, String disabledToolsJson) {
        this.enabledToolsJson = WorkbenchJsonValue.arrayOrDefault(enabledToolsJson, "enabledToolsJson");
        this.disabledToolsJson = WorkbenchJsonValue.arrayOrDefault(disabledToolsJson, "disabledToolsJson");
    }

    public Long getId() {
        return id;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public String getMcpServerId() {
        return mcpServerId;
    }

    public WorkbenchMcpCatalogSource getCatalogSource() {
        return catalogSource;
    }

    public String getRuntimeEndpointRef() {
        return runtimeEndpointRef;
    }

    public String getEnabledToolsJson() {
        return enabledToolsJson;
    }

    public String getDisabledToolsJson() {
        return disabledToolsJson;
    }

    public String getToolPolicyJson() {
        return toolPolicyJson;
    }

    public String getPolicyVersion() {
        return policyVersion;
    }

    public WorkbenchMcpBindingStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
