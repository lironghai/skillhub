package com.iflytek.skillhub.workbench.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.mcp.McpAssociatedItemResponse;
import com.iflytek.skillhub.mcp.McpCatalogService;
import com.iflytek.skillhub.mcp.McpInternalServerItemResponse;
import com.iflytek.skillhub.mcp.McpInternalServerResponse;
import com.iflytek.skillhub.workbench.application.CreateWorkbenchSessionCommand;
import com.iflytek.skillhub.workbench.application.ImportWorkbenchSourceResult;
import com.iflytek.skillhub.workbench.application.WorkbenchDiffResult;
import com.iflytek.skillhub.workbench.application.WorkbenchFileContent;
import com.iflytek.skillhub.workbench.application.WorkbenchMcpBindingSelection;
import com.iflytek.skillhub.workbench.application.WorkbenchMcpBindingUpdateCommand;
import com.iflytek.skillhub.workbench.application.WorkbenchPackagePreviewResult;
import com.iflytek.skillhub.workbench.application.WorkbenchPackagePublishCommand;
import com.iflytek.skillhub.workbench.application.WorkbenchPackagePublishResult;
import com.iflytek.skillhub.workbench.application.WorkbenchSessionApplicationService;
import com.iflytek.skillhub.workbench.domain.WorkbenchMode;
import com.iflytek.skillhub.workbench.domain.WorkbenchMcpBinding;
import com.iflytek.skillhub.workbench.domain.WorkbenchSession;
import com.iflytek.skillhub.workbench.domain.WorkbenchSessionEvent;
import com.iflytek.skillhub.workbench.domain.WorkbenchToolApproval;
import com.iflytek.skillhub.workbench.port.AgentRuntimeEvent;
import com.iflytek.skillhub.workbench.port.AgentRuntimeRunHandle;
import com.iflytek.skillhub.workbench.port.AgentRuntimeStreamListener;
import com.iflytek.skillhub.workbench.port.WorkbenchWorkspaceFile;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/web/workbench")
public class WorkbenchController extends BaseApiController {

    private static final Logger log = LoggerFactory.getLogger(WorkbenchController.class);
    private static final String DEFAULT_CREATE_VERSION = "0.1.0";
    private static final ObjectMapper JSON = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final WorkbenchSessionApplicationService workbenchService;
    private final NamespaceRepository namespaceRepository;
    private final SkillRepository skillRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final McpCatalogService mcpCatalogService;
    private final Clock clock;
    private final WorkbenchRuntimeConfigResponse runtimeConfig;

    public WorkbenchController(
            WorkbenchSessionApplicationService workbenchService,
            NamespaceRepository namespaceRepository,
            SkillRepository skillRepository,
            SkillVersionRepository skillVersionRepository,
            McpCatalogService mcpCatalogService,
            ApiResponseFactory responseFactory,
            Clock clock,
            @Value("${skillhub.workbench.runtime.model-executor-enabled:false}") boolean modelExecutorEnabled,
            @Value("${skillhub.workbench.runtime.model-provider:}") String modelProvider,
            @Value("${skillhub.workbench.runtime.model-name:}") String modelName,
            @Value("${skillhub.workbench.runtime.model-base-url:}") String modelBaseUrl,
            @Value("${skillhub.workbench.runtime.model-api-key:}") String modelApiKey) {
        super(responseFactory);
        this.workbenchService = workbenchService;
        this.namespaceRepository = namespaceRepository;
        this.skillRepository = skillRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.mcpCatalogService = mcpCatalogService;
        this.clock = clock;
        this.runtimeConfig = WorkbenchRuntimeConfigResponse.from(
                modelExecutorEnabled,
                modelProvider,
                modelName,
                modelBaseUrl,
                modelApiKey);
    }

    @GetMapping("/runtime-config")
    public ApiResponse<WorkbenchRuntimeConfigResponse> runtimeConfig() {
        return ok("response.success.read", runtimeConfig);
    }

    @PostMapping("/sessions")
    public ApiResponse<WorkbenchSessionResponse> createSession(
            @Valid @RequestBody CreateSessionRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        Namespace namespace = namespaceRepository.findBySlug(request.namespace())
                .orElseThrow(() -> new DomainBadRequestException("error.namespace.slug.notFound", request.namespace()));
        SourceSelection source = resolveSource(request);
        String targetSlug = resolveTargetSlug(request, source);
        String targetVersion = blankToDefault(
                request.targetVersion(),
                source == null ? DEFAULT_CREATE_VERSION : source.version().getVersion());

        WorkbenchSession session = workbenchService.createSession(new CreateWorkbenchSessionCommand(
                principal.userId(),
                namespace.getId(),
                request.mode(),
                source == null ? null : source.skill().getId(),
                source == null ? null : source.version().getId(),
                targetSlug,
                targetVersion,
                expiresAt(request.expiresInHours())));
        return ok("response.success.created", WorkbenchSessionResponse.from(session, 0, null));
    }

    @GetMapping("/sessions/{sessionId}")
    public ApiResponse<WorkbenchSessionResponse> getSession(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        WorkbenchSession session = workbenchService.getSession(sessionId, principal.userId());
        int fileCount = workbenchService.listFiles(sessionId, principal.userId()).size();
        RuntimeRunResponse activeRun = workbenchService.findActiveRun(session, principal.userId())
                .map(RuntimeRunResponse::from)
                .orElse(null);
        return ok("response.success.read", WorkbenchSessionResponse.from(session, fileCount, activeRun));
    }

    @GetMapping("/sessions")
    public ApiResponse<List<WorkbenchSessionResponse>> listSessions(
            @RequestParam(defaultValue = "20") int limit,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        List<WorkbenchSessionResponse> sessions = workbenchService.listSessions(principal.userId(), limit)
                .stream()
                .map(session -> WorkbenchSessionResponse.from(
                        session,
                        0,
                        workbenchService.findActiveRun(session, principal.userId())
                                .map(RuntimeRunResponse::from)
                                .orElse(null)))
                .toList();
        return ok("response.success.read", sessions);
    }

    @PostMapping("/sessions/{sessionId}/import-source")
    public ApiResponse<ImportWorkbenchSourceResult> importSource(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        return ok("response.success.created", workbenchService.importSourceVersion(sessionId, principal.userId()));
    }

    @GetMapping("/sessions/{sessionId}/events")
    public ApiResponse<List<WorkbenchSessionEventResponse>> listEvents(
            @PathVariable Long sessionId,
            @RequestParam(required = false) Long afterEventId,
            @RequestParam(defaultValue = "100") int limit,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        List<WorkbenchSessionEventResponse> events = workbenchService
                .listEvents(sessionId, principal.userId(), afterEventId, limit)
                .stream()
                .map(WorkbenchSessionEventResponse::from)
                .toList();
        return ok("response.success.read", events);
    }

    @GetMapping("/sessions/{sessionId}/files")
    public ApiResponse<List<WorkbenchWorkspaceFile>> listFiles(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        return ok("response.success.read", workbenchService.listFiles(sessionId, principal.userId()));
    }

    @GetMapping("/sessions/{sessionId}/mcp-catalog")
    public ApiResponse<WorkbenchMcpCatalogResponse> mcpCatalog(
            @PathVariable Long sessionId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") String page,
            @RequestParam(defaultValue = "20") String size,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        workbenchService.getSession(sessionId, principal.userId());
        McpInternalServerResponse catalog = mcpCatalogService.internalServers(search, page, size);
        return ok("response.success.read", WorkbenchMcpCatalogResponse.from(catalog));
    }

    @GetMapping("/sessions/{sessionId}/mcp-bindings")
    public ApiResponse<List<WorkbenchMcpBindingResponse>> mcpBindings(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        return ok("response.success.read", workbenchService.listMcpBindings(sessionId, principal.userId())
                .stream()
                .map(WorkbenchMcpBindingResponse::from)
                .toList());
    }

    @PostMapping("/sessions/{sessionId}/mcp-bindings")
    public ApiResponse<List<WorkbenchMcpBindingResponse>> updateMcpBindings(
            @PathVariable Long sessionId,
            @Valid @RequestBody UpdateMcpBindingsRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        return ok("response.success.updated", workbenchService.updateMcpBindings(
                        sessionId,
                        principal.userId(),
                        new WorkbenchMcpBindingUpdateCommand(request.serverIds(), request.bindings()))
                .stream()
                .map(WorkbenchMcpBindingResponse::from)
                .toList());
    }

    @GetMapping("/sessions/{sessionId}/approvals")
    public ApiResponse<List<WorkbenchToolApprovalResponse>> approvals(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        return ok("response.success.read", workbenchService.listPendingApprovals(sessionId, principal.userId())
                .stream()
                .map(WorkbenchToolApprovalResponse::from)
                .toList());
    }

    @GetMapping("/sessions/{sessionId}/file")
    public ApiResponse<WorkbenchFileResponse> readFile(
            @PathVariable Long sessionId,
            @RequestParam("path") String path,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        WorkbenchFileContent file = workbenchService.readFile(sessionId, principal.userId(), path);
        String content = new String(file.content(), StandardCharsets.UTF_8);
        return ok("response.success.read", new WorkbenchFileResponse(
                file.path(),
                content,
                file.contentType(),
                file.content().length));
    }

    @PutMapping("/sessions/{sessionId}/file")
    public ApiResponse<WorkbenchFileResponse> writeFile(
            @PathVariable Long sessionId,
            @RequestParam("path") String path,
            @Valid @RequestBody WriteFileRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        byte[] content = request.content().getBytes(StandardCharsets.UTF_8);
        workbenchService.writeFile(sessionId, principal.userId(), path, content, request.contentType());
        return ok("response.success.updated", new WorkbenchFileResponse(
                path,
                request.content(),
                request.contentType(),
                content.length));
    }

    @DeleteMapping("/sessions/{sessionId}/file")
    public ApiResponse<Void> deleteFile(
            @PathVariable Long sessionId,
            @RequestParam("path") String path,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        workbenchService.deleteFile(sessionId, principal.userId(), path);
        return ok("response.success.deleted", null);
    }

    @GetMapping("/sessions/{sessionId}/diff")
    public ApiResponse<WorkbenchDiffResult> diff(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        return ok("response.success.read", workbenchService.diff(sessionId, principal.userId()));
    }

    @PostMapping("/sessions/{sessionId}/approvals/{approvalId}/approve")
    public ApiResponse<WorkbenchToolApprovalResponse> approve(
            @PathVariable Long sessionId,
            @PathVariable Long approvalId,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        return ok("response.success.updated", WorkbenchToolApprovalResponse.from(
                workbenchService.approveToolApproval(sessionId, approvalId, principal.userId())));
    }

    @PostMapping("/sessions/{sessionId}/approvals/{approvalId}/reject")
    public ApiResponse<WorkbenchToolApprovalResponse> reject(
            @PathVariable Long sessionId,
            @PathVariable Long approvalId,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        return ok("response.success.updated", WorkbenchToolApprovalResponse.from(
                workbenchService.rejectToolApproval(sessionId, approvalId, principal.userId())));
    }

    @PostMapping("/sessions/{sessionId}/ready-for-review")
    public ApiResponse<WorkbenchSessionResponse> readyForReview(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        WorkbenchSession session = workbenchService.markReadyForReview(sessionId, principal.userId());
        int fileCount = workbenchService.listFiles(sessionId, principal.userId()).size();
        return ok("response.success.updated", WorkbenchSessionResponse.from(session, fileCount, null));
    }

    @PostMapping("/sessions/{sessionId}/package-preview")
    public ApiResponse<WorkbenchPackagePreviewResult> packagePreview(
            @PathVariable Long sessionId,
            @RequestBody(required = false) PackagePreviewRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        SkillVisibility visibility = visibility(request == null ? null : request.visibility());
        return ok("response.success.created", workbenchService.previewPackage(
                sessionId,
                principal.userId(),
                visibility,
                principal.platformRoles()));
    }

    @PostMapping("/sessions/{sessionId}/publish")
    public ApiResponse<WorkbenchPackagePublishResult> publish(
            @PathVariable Long sessionId,
            @Valid @RequestBody PublishPackageRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        return ok("response.success.published", workbenchService.publishPackage(
                sessionId,
                principal.userId(),
                new WorkbenchPackagePublishCommand(
                        request.confirmPackageFingerprint(),
                        visibility(request.visibility()),
                        principal.platformRoles())));
    }

    @PostMapping("/sessions/{sessionId}/messages")
    public ApiResponse<RuntimeRunResponse> sendMessage(
            @PathVariable Long sessionId,
            @Valid @RequestBody SendMessageRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        AgentRuntimeRunHandle handle = workbenchService.sendMessage(sessionId, principal.userId(), request.message());
        return ok("response.success.created", RuntimeRunResponse.from(handle));
    }

    @PostMapping(
            value = "/sessions/{sessionId}/messages/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public void sendMessageStream(
            @PathVariable Long sessionId,
            @Valid @RequestBody SendMessageRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletResponse response) throws IOException {
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform");
        response.setHeader(HttpHeaders.CONNECTION, "keep-alive");
        response.setHeader("X-Accel-Buffering", "no");
        String userId = principal.userId();
        String message = request.message();
        OutputStream output = response.getOutputStream();
        try {
            writeSse(output, "stream_open", new StreamOpenResponse("open"));
            AgentRuntimeRunHandle handle = workbenchService.sendMessageStreaming(
                    sessionId,
                    userId,
                    message,
                    new WorkbenchSseRuntimeListener(output));
            writeSse(output, "completed", RuntimeRunResponse.from(handle));
        } catch (Exception ex) {
            log.warn("Workbench runtime stream failed for session {}", sessionId, ex);
            writeSse(output, "error", new StreamErrorResponse(ex.getMessage()));
        }
    }

    @PostMapping("/sessions/{sessionId}/runs/{runId}/cancel")
    public ApiResponse<RuntimeRunResponse> cancelRun(
            @PathVariable Long sessionId,
            @PathVariable String runId,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        AgentRuntimeRunHandle handle = workbenchService.cancelRun(sessionId, principal.userId(), runId);
        return ok("response.success.updated", RuntimeRunResponse.from(handle));
    }

    private SourceSelection resolveSource(CreateSessionRequest request) {
        if (request.mode() == WorkbenchMode.CREATE_SKILL) {
            return null;
        }
        if (request.sourceSkill() == null) {
            throw new DomainBadRequestException("error.badRequest");
        }
        SourceSkillRequest source = request.sourceSkill();
        List<Skill> skills = skillRepository.findByNamespaceSlugAndSlug(source.namespace(), source.slug());
        Skill skill = skills.stream()
                .findFirst()
                .orElseThrow(() -> new SecurityException("source skill is unavailable"));
        SkillVersion version = skillVersionRepository.findBySkillIdAndVersion(skill.getId(), source.version())
                .orElseThrow(() -> new SecurityException("source version is unavailable"));
        return new SourceSelection(skill, version);
    }

    private String resolveTargetSlug(CreateSessionRequest request, SourceSelection source) {
        if (request.targetSlug() != null && !request.targetSlug().isBlank()) {
            return request.targetSlug();
        }
        if (source != null) {
            return source.skill().getSlug();
        }
        throw new DomainBadRequestException("error.badRequest");
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private SkillVisibility visibility(String value) {
        if (value == null || value.isBlank()) {
            return SkillVisibility.PRIVATE;
        }
        try {
            return SkillVisibility.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new DomainBadRequestException("error.badRequest");
        }
    }

    private static String sanitizeRuntimeResponseMessage(String message) {
        if (message == null || message.isBlank()) {
            return message;
        }
        return message
                .replaceAll("(?i)(token|password|secret|credential|authorization|cookie|api[-_ ]?key)\\s*[:=]\\s*(?:bearer\\s+)?[^\\s,;]+", "$1=[REDACTED]")
                .replaceAll("(?i)https?://[^\\s,;]+", "[REDACTED_URL]");
    }

    private Instant expiresAt(Integer expiresInHours) {
        int hours = expiresInHours == null ? 24 : Math.max(1, Math.min(expiresInHours, 168));
        return Instant.now(clock).plus(hours, ChronoUnit.HOURS);
    }

    public record CreateSessionRequest(
            @NotBlank String namespace,
            WorkbenchMode mode,
            @Valid
            SourceSkillRequest sourceSkill,
            String targetSlug,
            String targetVersion,
            Integer expiresInHours) {
        public CreateSessionRequest {
            if (mode == null) {
                mode = WorkbenchMode.CREATE_SKILL;
            }
        }
    }

    public record SourceSkillRequest(
            @NotBlank String namespace,
            @NotBlank String slug,
            @NotBlank String version) {
    }

    public record WriteFileRequest(
            @NotNull String content,
            String contentType) {
        public WriteFileRequest {
            if (contentType == null || contentType.isBlank()) {
                contentType = "text/markdown; charset=utf-8";
            }
        }
    }

    public record SendMessageRequest(@NotBlank String message) {
    }

    public record UpdateMcpBindingsRequest(
            List<String> serverIds,
            List<WorkbenchMcpBindingSelection> bindings) {
    }

    public record PackagePreviewRequest(String visibility) {
    }

    public record PublishPackageRequest(
            @NotBlank String confirmPackageFingerprint,
            @NotBlank String visibility) {
    }

    public record RuntimeRunResponse(
            String runId,
            String status,
            String message,
            Integer eventCount,
            Instant startedAt) {
        static RuntimeRunResponse from(AgentRuntimeRunHandle handle) {
            return new RuntimeRunResponse(
                    handle.runId(),
                    handle.status().name(),
                    sanitizeRuntimeResponseMessage(handle.message()),
                    handle.events().size(),
                    handle.startedAt());
        }
    }

    public record WorkbenchRuntimeConfigResponse(
            boolean modelExecutorEnabled,
            boolean modelConfigured,
            String modelProvider,
            String modelName,
            boolean modelBaseUrlConfigured,
            boolean modelApiKeyConfigured,
            String displayStatus,
            String message) {
        static WorkbenchRuntimeConfigResponse from(
                boolean modelExecutorEnabled,
                String modelProvider,
                String modelName,
                String modelBaseUrl,
                String modelApiKey) {
            String provider = normalizeOptional(modelProvider);
            String name = normalizeOptional(modelName);
            boolean hasBaseUrl = normalizeOptional(modelBaseUrl) != null;
            boolean hasApiKey = normalizeOptional(modelApiKey) != null;
            boolean configured = modelExecutorEnabled && provider != null && name != null && hasBaseUrl && hasApiKey;
            String status = configured ? "MODEL_CONFIGURED" : "MODEL_NOT_CONFIGURED";
            String message = configured
                    ? "模型配置已读取；真实执行能力以运行时适配器接入状态为准。"
                    : "模型执行器未配置；当前只创建 AgentScope 会话上下文，不会调用真实模型。";
            return new WorkbenchRuntimeConfigResponse(
                    modelExecutorEnabled,
                    configured,
                    provider,
                    name,
                    hasBaseUrl,
                    hasApiKey,
                    status,
                    message);
        }

        private static String normalizeOptional(String value) {
            return value == null || value.isBlank() ? null : value.trim();
        }
    }

    public record WorkbenchSessionResponse(
            Long id,
            String status,
            String mode,
            Long namespaceId,
            String targetSlug,
            String targetVersion,
            Integer fileCount,
            Instant expiresAt,
            RuntimeRunResponse activeRun) {
        static WorkbenchSessionResponse from(WorkbenchSession session, int fileCount, RuntimeRunResponse activeRun) {
            return new WorkbenchSessionResponse(
                    session.getId(),
                    session.getStatus().name(),
                    session.getMode().name(),
                    session.getNamespaceId(),
                    session.getTargetSlug(),
                    session.getTargetVersion(),
                    fileCount,
                    session.getExpiresAt(),
                    activeRun);
        }
    }

    public record WorkbenchSessionEventResponse(
            Long eventId,
            String type,
            Instant createdAt,
            String payloadJson) {
        static WorkbenchSessionEventResponse from(WorkbenchSessionEvent event) {
            return new WorkbenchSessionEventResponse(
                    event.getId(),
                    event.getEventType().name(),
                    event.getCreatedAt(),
                    event.getPayloadJson());
        }
    }

    public record WorkbenchFileResponse(
            String path,
            String content,
            String contentType,
            long sizeBytes) {
    }

    public record WorkbenchMcpCatalogResponse(
            List<WorkbenchMcpCatalogItemResponse> items,
            long total,
            int page,
            int size) {
        static WorkbenchMcpCatalogResponse from(McpInternalServerResponse response) {
            return new WorkbenchMcpCatalogResponse(
                    response.items().stream().map(WorkbenchMcpCatalogItemResponse::from).toList(),
                    response.total(),
                    response.page(),
                    response.size());
        }
    }

    public record WorkbenchMcpCatalogItemResponse(
            String id,
            String name,
            String description,
            boolean enabled,
            List<McpAssociatedItemResponse> tools,
            List<McpAssociatedItemResponse> resources,
            List<McpAssociatedItemResponse> prompts,
            List<String> tags,
            String catalogSource,
            boolean runtimeCandidate) {
        static WorkbenchMcpCatalogItemResponse from(McpInternalServerItemResponse item) {
            return new WorkbenchMcpCatalogItemResponse(
                    item.id(),
                    item.name(),
                    item.description(),
                    item.enabled(),
                    item.tools(),
                    item.resources(),
                    item.prompts(),
                    item.tags(),
                    "CONTEXT_FORGE",
                    item.enabled());
        }
    }

    public record WorkbenchMcpBindingResponse(
            Long id,
            String serverId,
            String catalogSource,
            JsonNode enabledToolsJson,
            JsonNode disabledToolsJson,
            JsonNode toolPolicyJson,
            String policyVersion,
            String status) {
        static WorkbenchMcpBindingResponse from(WorkbenchMcpBinding binding) {
            return new WorkbenchMcpBindingResponse(
                    binding.getId(),
                    binding.getMcpServerId(),
                    binding.getCatalogSource().name(),
                    jsonValue(binding.getEnabledToolsJson(), true),
                    jsonValue(binding.getDisabledToolsJson(), true),
                    jsonValue(binding.getToolPolicyJson(), false),
                    binding.getPolicyVersion(),
                    binding.getStatus().name());
        }
    }

    public record WorkbenchToolApprovalResponse(
            Long id,
            Long sessionId,
            Long eventId,
            String toolName,
            String mcpServerId,
            String riskLevel,
            JsonNode argumentsRedactedJson,
            String status,
            String decisionBy,
            Instant decisionAt,
            Instant createdAt) {
        static WorkbenchToolApprovalResponse from(WorkbenchToolApproval approval) {
            return new WorkbenchToolApprovalResponse(
                    approval.getId(),
                    approval.getSessionId(),
                    approval.getEventId(),
                    approval.getToolName(),
                    approval.getMcpServerId(),
                    approval.getRiskLevel().name(),
                    jsonValue(approval.getArgumentsRedactedJson(), false),
                    approval.getStatus().name(),
                    approval.getDecisionBy(),
                    approval.getDecisionAt(),
                    approval.getCreatedAt());
        }
    }

    private static JsonNode jsonValue(String rawJson, boolean arrayDefault) {
        try {
            JsonNode node = JSON.readTree(rawJson == null || rawJson.isBlank()
                    ? (arrayDefault ? "[]" : "{}")
                    : rawJson);
            if (node != null && node.isTextual()) {
                node = JSON.readTree(node.asText());
            }
            if (node != null && ((arrayDefault && node.isArray()) || (!arrayDefault && node.isObject()))) {
                return node;
            }
        } catch (JsonProcessingException ignored) {
            // Fall through to a safe container default.
        }
        return arrayDefault ? JSON.createArrayNode() : JSON.createObjectNode();
    }

    private static void writeSse(OutputStream output, String event, Object data) throws IOException {
        output.write(("event: " + event + "\n").getBytes(StandardCharsets.UTF_8));
        String json = JSON.writeValueAsString(data);
        String[] lines = json.split("\\R", -1);
        for (String line : lines) {
            output.write(("data: " + line + "\n").getBytes(StandardCharsets.UTF_8));
        }
        output.write('\n');
        output.flush();
    }

    private static final class WorkbenchSseRuntimeListener implements AgentRuntimeStreamListener {
        private final OutputStream output;

        private WorkbenchSseRuntimeListener(OutputStream output) {
            this.output = output;
        }

        @Override
        public void onRunStarted(String runId) {
            writeUnchecked("run_started", new StreamRunStartedResponse(runId));
        }

        @Override
        public void onDelta(String phase, String content) {
            String event = "thinking".equals(phase) ? "thinking_delta" : "message_delta";
            writeUnchecked(event, new StreamDeltaResponse(content));
        }

        @Override
        public void onRuntimeEvent(AgentRuntimeEvent event) {
            writeUnchecked("runtime_event", RuntimeEventStreamResponse.from(event));
        }

        private void writeUnchecked(String event, Object data) {
            try {
                writeSse(output, event, data);
            } catch (IOException ex) {
                throw new IllegalStateException("stream write failed", ex);
            }
        }
    }

    private record StreamRunStartedResponse(String runId) {
    }

    private record StreamDeltaResponse(String content) {
    }

    private record StreamOpenResponse(String status) {
    }

    private record StreamErrorResponse(String message) {
    }

    private record RuntimeEventStreamResponse(String type, String payloadJson) {
        private static RuntimeEventStreamResponse from(AgentRuntimeEvent event) {
            return new RuntimeEventStreamResponse(event.type().name(), event.payloadJson());
        }
    }

    private record SourceSelection(Skill skill, SkillVersion version) {
    }
}
