package com.iflytek.skillhub.workbench.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.domain.skill.validation.SkillPackagePolicy;
import com.iflytek.skillhub.workbench.domain.WorkbenchFileSnapshot;
import com.iflytek.skillhub.workbench.domain.WorkbenchFileSnapshotType;
import com.iflytek.skillhub.workbench.domain.WorkbenchMode;
import com.iflytek.skillhub.workbench.domain.WorkbenchMcpBinding;
import com.iflytek.skillhub.workbench.domain.WorkbenchMcpBindingStatus;
import com.iflytek.skillhub.workbench.domain.WorkbenchPublishCandidate;
import com.iflytek.skillhub.workbench.domain.WorkbenchPublishValidationStatus;
import com.iflytek.skillhub.workbench.domain.WorkbenchSession;
import com.iflytek.skillhub.workbench.domain.WorkbenchSessionEvent;
import com.iflytek.skillhub.workbench.domain.WorkbenchSessionEventType;
import com.iflytek.skillhub.workbench.domain.WorkbenchSessionStatus;
import com.iflytek.skillhub.workbench.domain.WorkbenchToolApproval;
import com.iflytek.skillhub.workbench.domain.WorkbenchToolRiskLevel;
import com.iflytek.skillhub.workbench.port.AgentRuntimeCancelCommand;
import com.iflytek.skillhub.workbench.port.AgentRuntimeEvent;
import com.iflytek.skillhub.workbench.port.AgentRuntimeEventType;
import com.iflytek.skillhub.workbench.port.AgentRuntimeMcpServer;
import com.iflytek.skillhub.workbench.port.AgentRuntimePort;
import com.iflytek.skillhub.workbench.port.AgentRuntimeRunCommand;
import com.iflytek.skillhub.workbench.port.AgentRuntimeRunHandle;
import com.iflytek.skillhub.workbench.port.AgentRuntimeRunStatus;
import com.iflytek.skillhub.workbench.port.AgentRuntimeStreamListener;
import com.iflytek.skillhub.workbench.port.AgentRuntimeWorkspaceFile;
import com.iflytek.skillhub.workbench.port.WorkbenchAuthorizationPort;
import com.iflytek.skillhub.workbench.port.WorkbenchFileSnapshotRepository;
import com.iflytek.skillhub.workbench.port.WorkbenchMcpBindingRepository;
import com.iflytek.skillhub.workbench.port.WorkbenchMcpCatalogPort;
import com.iflytek.skillhub.workbench.port.WorkbenchMcpRuntimeCandidate;
import com.iflytek.skillhub.workbench.port.WorkbenchPublishCandidateRepository;
import com.iflytek.skillhub.workbench.port.WorkbenchSessionEventRepository;
import com.iflytek.skillhub.workbench.port.WorkbenchSessionRepository;
import com.iflytek.skillhub.workbench.port.WorkbenchSkillPublishPort;
import com.iflytek.skillhub.workbench.port.WorkbenchSkillSourcePort;
import com.iflytek.skillhub.workbench.port.WorkbenchSourceFile;
import com.iflytek.skillhub.workbench.port.WorkbenchToolApprovalRepository;
import com.iflytek.skillhub.workbench.port.WorkbenchWorkspaceFile;
import com.iflytek.skillhub.workbench.port.WorkbenchWorkspaceStoragePort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class WorkbenchSessionApplicationService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String DEFAULT_POLICY_VERSION = "v1";
    private static final String RUNTIME_ARTIFACT_REASON = "WORKBENCH_RUNTIME_ARTIFACT";

    private final WorkbenchSessionRepository sessionRepository;
    private final WorkbenchSessionEventRepository eventRepository;
    private final WorkbenchFileSnapshotRepository fileSnapshotRepository;
    private final WorkbenchMcpBindingRepository mcpBindingRepository;
    private final WorkbenchMcpCatalogPort mcpCatalogPort;
    private final WorkbenchToolApprovalRepository toolApprovalRepository;
    private final WorkbenchPublishCandidateRepository publishCandidateRepository;
    private final WorkbenchSkillPublishPort skillPublishPort;
    private final WorkbenchSkillSourcePort skillSourcePort;
    private final WorkbenchWorkspaceStoragePort workspaceStoragePort;
    private final WorkbenchAuthorizationPort authorizationPort;
    private final AgentRuntimePort runtimePort;
    private final Clock clock;

    public WorkbenchSessionApplicationService(
            WorkbenchSessionRepository sessionRepository,
            WorkbenchSessionEventRepository eventRepository,
            WorkbenchFileSnapshotRepository fileSnapshotRepository,
            WorkbenchMcpBindingRepository mcpBindingRepository,
            WorkbenchMcpCatalogPort mcpCatalogPort,
            WorkbenchToolApprovalRepository toolApprovalRepository,
            WorkbenchPublishCandidateRepository publishCandidateRepository,
            WorkbenchSkillPublishPort skillPublishPort,
            WorkbenchSkillSourcePort skillSourcePort,
            WorkbenchWorkspaceStoragePort workspaceStoragePort,
            WorkbenchAuthorizationPort authorizationPort,
            AgentRuntimePort runtimePort,
            Clock clock) {
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository");
        this.eventRepository = Objects.requireNonNull(eventRepository, "eventRepository");
        this.fileSnapshotRepository = Objects.requireNonNull(fileSnapshotRepository, "fileSnapshotRepository");
        this.mcpBindingRepository = Objects.requireNonNull(mcpBindingRepository, "mcpBindingRepository");
        this.mcpCatalogPort = Objects.requireNonNull(mcpCatalogPort, "mcpCatalogPort");
        this.toolApprovalRepository = Objects.requireNonNull(toolApprovalRepository, "toolApprovalRepository");
        this.publishCandidateRepository = Objects.requireNonNull(publishCandidateRepository, "publishCandidateRepository");
        this.skillPublishPort = Objects.requireNonNull(skillPublishPort, "skillPublishPort");
        this.skillSourcePort = Objects.requireNonNull(skillSourcePort, "skillSourcePort");
        this.workspaceStoragePort = Objects.requireNonNull(workspaceStoragePort, "workspaceStoragePort");
        this.authorizationPort = Objects.requireNonNull(authorizationPort, "authorizationPort");
        this.runtimePort = runtimePort;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public WorkbenchSession createSession(CreateWorkbenchSessionCommand command) {
        authorizationPort.assertCanCreateSession(command);
        WorkbenchSession session = command.mode() == WorkbenchMode.CREATE_SKILL
                ? WorkbenchSession.createSkill(command.userId(), command.namespaceId(),
                command.targetSlug(), command.targetVersion(), workspaceKey(command), command.expiresAt(), clock)
                : WorkbenchSession.updateSkill(command.userId(), command.namespaceId(),
                command.sourceSkillId(), command.sourceVersionId(), command.targetSlug(), command.targetVersion(),
                workspaceKey(command), command.expiresAt(), clock);
        WorkbenchSession saved = sessionRepository.save(session);
        audit(saved.getId(), "session.created", Map.of(
                "mode", saved.getMode().name(),
                "userId", saved.getUserId(),
                "namespaceId", saved.getNamespaceId()));
        return saved;
    }

    @Transactional(readOnly = true)
    public WorkbenchSession getSession(Long sessionId, String userId) {
        return loadAuthorizedSession(sessionId, userId);
    }

    @Transactional(readOnly = true)
    public List<WorkbenchSession> listSessions(String userId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        return sessionRepository.findByUserId(userId, safeLimit).stream()
                .peek(session -> authorizationPort.assertCanAccessSession(userId, session))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<AgentRuntimeRunHandle> findActiveRun(Long sessionId, String userId) {
        WorkbenchSession session = loadAuthorizedSession(sessionId, userId);
        return findActiveRun(session, userId);
    }

    @Transactional(readOnly = true)
    public Optional<AgentRuntimeRunHandle> findActiveRun(WorkbenchSession session, String userId) {
        authorizationPort.assertCanAccessSession(userId, session);
        if (runtimePort == null) {
            return Optional.empty();
        }
        return runtimePort.findActiveRun(userId, session.getId());
    }

    @Transactional(readOnly = true)
    public List<WorkbenchSessionEvent> listEvents(Long sessionId, String userId, Long afterEventId, int limit) {
        loadAuthorizedSession(sessionId, userId);
        int safeLimit = Math.max(1, Math.min(limit, 200));
        if (afterEventId == null) {
            return eventRepository.findBySessionId(sessionId, safeLimit);
        }
        return eventRepository.findBySessionIdAfterEventId(sessionId, afterEventId, safeLimit);
    }

    public ImportWorkbenchSourceResult importSourceVersion(Long sessionId, String userId) {
        WorkbenchSession session = loadAuthorizedSession(sessionId, userId);
        assertEditableDraft(session);
        if (session.getMode() != WorkbenchMode.UPDATE_SKILL) {
            throw new DomainBadRequestException("error.workbench.import.updateSkillRequired");
        }
        authorizationPort.assertCanImportSourceVersion(userId, session);
        List<String> imported = new ArrayList<>();
        for (WorkbenchSourceFile sourceFile : skillSourcePort.listFiles(session.getSourceVersionId())) {
            String path = normalizePublicPath(sourceFile.path());
            byte[] content = skillSourcePort.readFile(sourceFile);
            validatePackageFile(session, path, content);
            String contentType = sourceFile.contentType();
            workspaceStoragePort.write(session.getWorkspaceKey(), path, content, contentType);
            replaceSnapshot(session, WorkbenchFileSnapshotType.CURRENT, path, content, contentType);
            createBaselineIfAbsent(session, path, content, contentType);
            imported.add(path);
        }
        imported.sort(String::compareTo);
        audit(session.getId(), "source.imported", Map.of(
                "sourceVersionId", session.getSourceVersionId(),
                "fileCount", imported.size()));
        return new ImportWorkbenchSourceResult(imported);
    }

    public List<WorkbenchWorkspaceFile> listFiles(Long sessionId, String userId) {
        WorkbenchSession session = loadAuthorizedSession(sessionId, userId);
        return fileSnapshotRepository.findBySessionIdAndType(session.getId(), WorkbenchFileSnapshotType.CURRENT)
                .stream()
                .map(snapshot -> new WorkbenchWorkspaceFile(
                        snapshot.getFilePath(), snapshot.getSizeBytes(), snapshot.getContentType()))
                .sorted(Comparator.comparing(WorkbenchWorkspaceFile::path))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WorkbenchMcpBinding> listMcpBindings(Long sessionId, String userId) {
        WorkbenchSession session = loadAuthorizedSession(sessionId, userId);
        return mcpBindingRepository.findBySessionId(session.getId());
    }

    public List<WorkbenchMcpBinding> updateMcpBindings(
            Long sessionId,
            String userId,
            WorkbenchMcpBindingUpdateCommand command) {
        WorkbenchSession session = loadAuthorizedSession(sessionId, userId);
        assertEditableWorkspace(session);
        LinkedHashSet<String> selectedServerIds = selectedServerIds(command);
        Map<String, WorkbenchMcpBindingSelection> selectionsByServer = selectionsByServer(command);
        Map<String, WorkbenchMcpRuntimeCandidate> candidatesByServer = runtimeCandidatesByServer(selectedServerIds);
        List<WorkbenchMcpBinding> existing = mcpBindingRepository.findBySessionId(session.getId());
        Map<String, WorkbenchMcpBinding> activeByServer = new LinkedHashMap<>();
        Map<String, WorkbenchMcpBinding> reusableByServer = new LinkedHashMap<>();
        for (WorkbenchMcpBinding binding : existing) {
            reusableByServer.putIfAbsent(binding.getMcpServerId(), binding);
            boolean alreadyActive = activeByServer.containsKey(binding.getMcpServerId());
            if (binding.getStatus() == WorkbenchMcpBindingStatus.ENABLED
                    && selectedServerIds.contains(binding.getMcpServerId())
                    && !alreadyActive) {
                activeByServer.put(binding.getMcpServerId(), binding);
            } else if (binding.getStatus() == WorkbenchMcpBindingStatus.ENABLED
                    && (!selectedServerIds.contains(binding.getMcpServerId())
                    || alreadyActive)) {
                binding.disable();
                mcpBindingRepository.save(binding);
            }
        }
        for (String serverId : selectedServerIds) {
            if (!activeByServer.containsKey(serverId)) {
                WorkbenchMcpRuntimeCandidate candidate = candidatesByServer.get(serverId);
                WorkbenchMcpBinding binding = reusableByServer.get(serverId);
                if (binding != null) {
                    binding.enable();
                } else {
                    WorkbenchMcpBindingSelection selection = selectionsByServer.get(serverId);
                    binding = new WorkbenchMcpBinding(
                            session.getId(),
                            candidate.serverId(),
                            candidate.catalogSource(),
                            candidate.runtimeEndpointRef(),
                            selection == null ? "[]" : jsonArray(selection.enabledTools()),
                            selection == null ? "[]" : jsonArray(selection.disabledTools()),
                            "{}",
                            DEFAULT_POLICY_VERSION,
                            clock);
                }
                activeByServer.put(serverId, mcpBindingRepository.save(binding));
            }
        }
        activeByServer.forEach((serverId, binding) -> {
            WorkbenchMcpBindingSelection selection = selectionsByServer.get(serverId);
            if (selection != null) {
                binding.updateToolSelection(
                        jsonArray(selection.enabledTools()),
                        jsonArray(selection.disabledTools()));
                mcpBindingRepository.save(binding);
            }
        });
        audit(session.getId(), "mcp.bindings.updated", Map.of("bindingCount", activeByServer.size()));
        return activeByServer.values().stream()
                .sorted(Comparator.comparing(WorkbenchMcpBinding::getMcpServerId))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WorkbenchToolApproval> listPendingApprovals(Long sessionId, String userId) {
        WorkbenchSession session = loadAuthorizedSession(sessionId, userId);
        return toolApprovalRepository.findPendingBySessionId(session.getId());
    }

    public WorkbenchFileContent readFile(Long sessionId, String userId, String filePath) {
        WorkbenchSession session = loadAuthorizedSession(sessionId, userId);
        String path = normalizePublicPath(filePath);
        byte[] content = workspaceStoragePort.read(session.getWorkspaceKey(), path)
                .orElseThrow(() -> new IllegalArgumentException("workspace file not found: " + path));
        String contentType = fileSnapshotRepository.findBySessionIdAndTypeAndFilePath(
                        session.getId(), WorkbenchFileSnapshotType.CURRENT, path)
                .map(WorkbenchFileSnapshot::getContentType)
                .orElse(null);
        return new WorkbenchFileContent(path, content, contentType);
    }

    public void writeFile(Long sessionId, String userId, String filePath, byte[] content, String contentType) {
        WorkbenchSession session = loadAuthorizedSession(sessionId, userId);
        assertEditableWorkspace(session);
        String path = normalizePublicPath(filePath);
        byte[] safeContent = Objects.requireNonNull(content, "content").clone();
        validatePackageFile(session, path, safeContent);
        workspaceStoragePort.write(session.getWorkspaceKey(), path, safeContent, contentType);
        replaceSnapshot(session, WorkbenchFileSnapshotType.CURRENT, path, safeContent, contentType);
        fileEvent(session.getId(), "file.written", path);
    }

    public void deleteFile(Long sessionId, String userId, String filePath) {
        WorkbenchSession session = loadAuthorizedSession(sessionId, userId);
        assertEditableWorkspace(session);
        String path = normalizePublicPath(filePath);
        workspaceStoragePort.delete(session.getWorkspaceKey(), path);
        fileSnapshotRepository.deleteBySessionIdAndTypeAndFilePath(
                session.getId(), WorkbenchFileSnapshotType.CURRENT, path);
        fileEvent(session.getId(), "file.deleted", path);
    }

    public WorkbenchDiffResult diff(Long sessionId, String userId) {
        WorkbenchSession session = loadAuthorizedSession(sessionId, userId);
        Map<String, WorkbenchFileSnapshot> baseline = snapshotsByPath(session.getId(), WorkbenchFileSnapshotType.BASELINE);
        Map<String, WorkbenchFileSnapshot> current = snapshotsByPath(session.getId(), WorkbenchFileSnapshotType.CURRENT);
        Set<String> paths = new LinkedHashSet<>();
        paths.addAll(baseline.keySet());
        paths.addAll(current.keySet());
        return new WorkbenchDiffResult(paths.stream()
                .sorted()
                .map(path -> new WorkbenchFileDiff(path, statusFor(baseline.get(path), current.get(path))))
                .toList());
    }

    public WorkbenchToolApproval approveToolApproval(Long sessionId, Long approvalId, String userId) {
        return decideApproval(sessionId, approvalId, userId, true);
    }

    public WorkbenchToolApproval rejectToolApproval(Long sessionId, Long approvalId, String userId) {
        return decideApproval(sessionId, approvalId, userId, false);
    }

    public WorkbenchSession markReadyForReview(Long sessionId, String userId) {
        WorkbenchSession session = loadAuthorizedSession(sessionId, userId);
        assertNoPendingApprovals(session);
        session.markReadyForReview(clock);
        WorkbenchSession saved = sessionRepository.save(session);
        statusEvent(saved, "status.changed");
        return saved;
    }

    public WorkbenchPackagePreviewResult previewPackage(
            Long sessionId,
            String userId,
            SkillVisibility visibility,
            Set<String> platformRoles) {
        WorkbenchSession session = loadAuthorizedSession(sessionId, userId);
        assertNoPendingApprovals(session);
        SkillVisibility effectiveVisibility = visibility == null ? SkillVisibility.PRIVATE : visibility;
        PackageBuildResult packageBuild = buildPackage(session);
        WorkbenchPublishValidationResult validation = skillPublishPort.validateOnly(
                session.getNamespaceId(),
                packageBuild.entries(),
                userId,
                effectiveVisibility,
                platformRoles == null ? Set.of() : Set.copyOf(platformRoles));
        validation = validateResolvedTarget(session, validation);
        WorkbenchPackageValidationReport report = validationReport(validation);
        String reportJson = validationReportJson(report, effectiveVisibility);
        WorkbenchPublishCandidate candidate = new WorkbenchPublishCandidate(
                session.getId(),
                packageBuild.fingerprint(),
                packageBuild.includedFiles().size(),
                packageBuild.totalSize(),
                effectiveVisibility,
                reportJson,
                clock);
        if (validation.valid()) {
            candidate.markValidationPassed(reportJson);
        } else {
            candidate.markValidationFailed(reportJson);
        }
        publishCandidateRepository.save(candidate);
        audit(session.getId(), "package.previewed", Map.of(
                "fingerprint", packageBuild.fingerprint(),
                "readyToPublish", validation.valid(),
                "includedFileCount", packageBuild.includedFiles().size(),
                "excludedFileCount", packageBuild.excludedFiles().size()));
        return new WorkbenchPackagePreviewResult(
                packageBuild.fingerprint(),
                validation.valid(),
                packageBuild.includedFiles(),
                packageBuild.excludedFiles(),
                report);
    }

    public WorkbenchPackagePublishResult publishPackage(
            Long sessionId,
            String userId,
            WorkbenchPackagePublishCommand command) {
        WorkbenchSession session = loadAuthorizedSession(sessionId, userId);
        assertNoPendingApprovals(session);
        if (session.getStatus() != WorkbenchSessionStatus.READY_FOR_REVIEW
                && session.getStatus() != WorkbenchSessionStatus.DRAFT
                && session.getStatus() != WorkbenchSessionStatus.RUNNING) {
            throw new DomainBadRequestException("error.workbench.publish.status.invalid");
        }
        SkillVisibility visibility = command == null || command.visibility() == null
                ? SkillVisibility.PRIVATE
                : command.visibility();
        String confirmedFingerprint = command == null ? null : command.confirmPackageFingerprint();
        WorkbenchSession.requireText(confirmedFingerprint, "confirmPackageFingerprint");
        WorkbenchPublishCandidate candidate = publishCandidateRepository.findLatestBySessionId(session.getId())
                .orElseThrow(() -> new DomainBadRequestException("error.workbench.publish.preview.required"));
        if (candidate.getValidationStatus() != WorkbenchPublishValidationStatus.PASSED) {
            throw new DomainBadRequestException("error.workbench.publish.preview.notReady");
        }
        if (visibility != candidate.getVisibility()) {
            throw new DomainBadRequestException("error.workbench.publish.visibility.mismatch");
        }
        if (!confirmedFingerprint.equals(candidate.getPackageFingerprint())) {
            throw new DomainBadRequestException("error.workbench.publish.fingerprint.previewMismatch");
        }

        PackageBuildResult packageBuild = buildPackage(session);
        if (!confirmedFingerprint.equals(packageBuild.fingerprint())) {
            throw new DomainBadRequestException("error.workbench.publish.fingerprint.workspaceMismatch");
        }

        WorkbenchPackagePublishResult result = skillPublishPort.publish(
                session.getNamespaceId(),
                packageBuild.entries(),
                userId,
                visibility,
                command.platformRoles() == null ? Set.of() : Set.copyOf(command.platformRoles()));

        if (session.getStatus() != WorkbenchSessionStatus.READY_FOR_REVIEW) {
            session.markReadyForReview(clock);
            statusEvent(session, "package.ready_for_review");
        }
        session.startPublishing(clock);
        statusEvent(session, "package.publishing");
        candidate.markPublished(result.skillVersionId());
        publishCandidateRepository.save(candidate);
        session.markPublished(clock);
        WorkbenchSession saved = sessionRepository.save(session);
        statusEvent(saved, "package.published");
        audit(saved.getId(), "package.published", Map.of(
                "skillId", result.skillId(),
                "skillVersionId", result.skillVersionId(),
                "fingerprint", packageBuild.fingerprint()));
        return result;
    }

    public AgentRuntimeRunHandle sendMessage(Long sessionId, String userId, String message) {
        return sendMessageInternal(sessionId, userId, message, null);
    }

    public AgentRuntimeRunHandle sendMessageStreaming(
            Long sessionId,
            String userId,
            String message,
            AgentRuntimeStreamListener listener) {
        return sendMessageInternal(sessionId, userId, message, listener);
    }

    private AgentRuntimeRunHandle sendMessageInternal(
            Long sessionId,
            String userId,
            String message,
            AgentRuntimeStreamListener listener) {
        AgentRuntimePort runtime = requireRuntimePort();
        String safeMessage = WorkbenchSession.requireText(message, "message");
        WorkbenchSession session = loadAuthorizedSession(sessionId, userId);
        if (session.isTerminal()
                || session.getStatus() == WorkbenchSessionStatus.READY_FOR_REVIEW
                || session.getStatus() == WorkbenchSessionStatus.PUBLISHING) {
            throw new DomainBadRequestException("error.workbench.runtime.status.invalid", session.getStatus());
        }
        if (session.getStatus() == WorkbenchSessionStatus.WAITING_APPROVAL) {
            throw new DomainBadRequestException("error.workbench.runtime.waitingApproval");
        }

        if (session.getStatus() == WorkbenchSessionStatus.DRAFT) {
            session.start(clock);
            sessionRepository.save(session);
            statusEvent(session, "runtime.started");
        }
        WorkbenchSessionEvent userEvent = eventRepository.append(new WorkbenchSessionEvent(
                session.getId(),
                WorkbenchSessionEventType.USER_MESSAGE,
                json(Map.of("message", safeMessage)),
                clock));

        try {
            AgentRuntimeRunCommand command = new AgentRuntimeRunCommand(
                    userId,
                    session.getId(),
                    session.getWorkspaceKey(),
                    runtimeSessionContext(session),
                    runtimeMcpServers(session.getId()),
                    runtimeWorkspaceFiles(session),
                    safeMessage);
            AgentRuntimeRunHandle handle = listener == null
                    ? runtime.start(command)
                    : runtime.startStreaming(command, new PersistingRuntimeStreamListener(
                            session, listener, userEvent.getId(), safeMessage));
            if (listener == null) {
                applyRuntimeEvents(session, handle, userEvent.getId(), safeMessage);
            }
            if (handle.status() == AgentRuntimeRunStatus.FAILED) {
                failSession(session, "runtime.failed", handle.message(), handle.runId());
            }
            return handle;
        } catch (RuntimeException ex) {
            failSession(session, "runtime.failed", ex.getMessage());
            throw ex;
        }
    }

    private final class PersistingRuntimeStreamListener implements AgentRuntimeStreamListener {
        private final WorkbenchSession session;
        private final AgentRuntimeStreamListener delegate;
        private final Long sourceUserEventId;
        private final String sourceUserContent;
        private String runId;

        private PersistingRuntimeStreamListener(
                WorkbenchSession session,
                AgentRuntimeStreamListener delegate,
                Long sourceUserEventId,
                String sourceUserContent) {
            this.session = session;
            this.delegate = delegate;
            this.sourceUserEventId = sourceUserEventId;
            this.sourceUserContent = sourceUserContent;
        }

        @Override
        public void onRunStarted(String runId) {
            this.runId = runId;
            delegate.onRunStarted(runId);
        }

        @Override
        public void onDelta(String phase, String content) {
            delegate.onDelta(phase, content);
        }

        @Override
        public void onRuntimeEvent(AgentRuntimeEvent event) {
            applyRuntimeEvent(session, event, runId, sourceUserEventId, sourceUserContent);
            delegate.onRuntimeEvent(event);
        }
    }

    public AgentRuntimeRunHandle cancelRun(Long sessionId, String userId, String runId) {
        AgentRuntimePort runtime = requireRuntimePort();
        WorkbenchSession session = loadAuthorizedSession(sessionId, userId);
        AgentRuntimeRunHandle handle = runtime.cancel(new AgentRuntimeCancelCommand(userId, sessionId, runId));
        applyRuntimeEvents(session, handle);
        if (handle.status() == AgentRuntimeRunStatus.CANCELLED && !session.isTerminal()) {
            session.cancel(clock);
            sessionRepository.save(session);
            statusEvent(session, "runtime.cancelled");
        } else if (handle.status() == AgentRuntimeRunStatus.FAILED) {
            failSession(session, "runtime.cancel_failed", handle.message(), handle.runId());
        } else if (handle.status() == AgentRuntimeRunStatus.REJECTED) {
            appendRuntimeEvent(session.getId(), WorkbenchSessionEventType.ERROR,
                    runtimePayloadWithRunId(json(Map.of("action", "runtime.cancel_rejected",
                            "message", sanitizeRuntimeMessage(handle.message()))), handle.runId()));
        }
        return handle;
    }

    private WorkbenchSession loadAuthorizedSession(Long sessionId, String userId) {
        WorkbenchSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("workbench session not found: " + sessionId));
        authorizationPort.assertCanAccessSession(userId, session);
        return session;
    }

    private static String runtimeSessionContext(WorkbenchSession session) {
        StringBuilder builder = new StringBuilder(512);
        builder.append("当前 SkillHub 工作台会话上下文：\n")
                .append("- 会话 ID: ").append(session.getId()).append('\n')
                .append("- 工作模式: ").append(session.getMode() == WorkbenchMode.CREATE_SKILL ? "创建技能" : "更新技能").append('\n')
                .append("- 命名空间 ID: ").append(session.getNamespaceId()).append('\n')
                .append("- 目标技能标识: ").append(session.getTargetSlug()).append('\n')
                .append("- 目标版本: ").append(session.getTargetVersion()).append('\n')
                .append("- 工作区 Key: ").append(session.getWorkspaceKey()).append('\n');
        if (session.getMode() == WorkbenchMode.UPDATE_SKILL) {
            builder.append("- 源技能 ID: ").append(session.getSourceSkillId()).append('\n')
                    .append("- 源版本 ID: ").append(session.getSourceVersionId()).append('\n')
                    .append("- 更新约束: 本次对话默认是在修改源技能，并产出目标技能标识和目标版本的新版本。\n");
        } else {
            builder.append("- 创建约束: 本次对话默认是在创建目标技能标识对应的新技能。\n");
        }
        builder.append("用户提到“当前技能”“这个技能”“它”时，默认指上述目标技能；不要要求用户重复提供目标标识或版本。");
        return builder.toString();
    }

    private AgentRuntimePort requireRuntimePort() {
        if (runtimePort == null) {
            throw new IllegalStateException("Agent runtime port is not configured");
        }
        return runtimePort;
    }

    private static void assertEditableDraft(WorkbenchSession session) {
        if (session.getStatus() != WorkbenchSessionStatus.DRAFT) {
            throw new DomainBadRequestException("error.workbench.session.notEditable", session.getStatus());
        }
    }

    private static void assertEditableWorkspace(WorkbenchSession session) {
        if (session.getStatus() != WorkbenchSessionStatus.DRAFT
                && session.getStatus() != WorkbenchSessionStatus.RUNNING) {
            throw new DomainBadRequestException("error.workbench.session.notEditable", session.getStatus());
        }
    }

    private void assertNoPendingApprovals(WorkbenchSession session) {
        if (!toolApprovalRepository.findPendingBySessionId(session.getId()).isEmpty()) {
            throw new DomainBadRequestException("error.workbench.session.pendingApprovals");
        }
    }

    private void createBaselineIfAbsent(WorkbenchSession session, String path, byte[] content, String contentType) {
        if (fileSnapshotRepository.findBySessionIdAndTypeAndFilePath(
                session.getId(), WorkbenchFileSnapshotType.BASELINE, path).isPresent()) {
            return;
        }
        String baselinePath = baselineStorageKey(session, path, sha256(content));
        workspaceStoragePort.write(session.getWorkspaceKey(), baselinePath, content, contentType);
        fileSnapshotRepository.save(new WorkbenchFileSnapshot(
                session.getId(), WorkbenchFileSnapshotType.BASELINE, path, baselinePath,
                sha256(content), (long) content.length, contentType, session.getExpiresAt(), clock));
    }

    private void applyRuntimeEvents(WorkbenchSession session, AgentRuntimeRunHandle handle) {
        applyRuntimeEvents(session, handle, null, null);
    }

    private void applyRuntimeEvents(
            WorkbenchSession session,
            AgentRuntimeRunHandle handle,
            Long sourceUserEventId,
            String sourceUserContent) {
        for (AgentRuntimeEvent event : handle.events()) {
            applyRuntimeEvent(session, event, handle.runId(), sourceUserEventId, sourceUserContent);
        }
    }

    private void applyRuntimeEvent(WorkbenchSession session, AgentRuntimeEvent event) {
        applyRuntimeEvent(session, event, null);
    }

    private void applyRuntimeEvent(WorkbenchSession session, AgentRuntimeEvent event, String runId) {
        applyRuntimeEvent(session, event, runId, null, null);
    }

    private void applyRuntimeEvent(
            WorkbenchSession session,
            AgentRuntimeEvent event,
            String runId,
            Long sourceUserEventId,
            String sourceUserContent) {
        if (event.type() == AgentRuntimeEventType.MODEL_MESSAGE) {
            appendRuntimeEvent(session.getId(), WorkbenchSessionEventType.MODEL_MESSAGE,
                    runtimePayloadWithRunContext(event.payloadJson(), runId, sourceUserEventId, sourceUserContent));
        } else if (event.type() == AgentRuntimeEventType.FILE_WRITTEN) {
            writeRuntimeFile(session, event);
        } else if (event.type() == AgentRuntimeEventType.FILE_DELETED) {
            deleteRuntimeFile(session, event);
        } else if (event.type() == AgentRuntimeEventType.TOOL_CALL) {
            appendRuntimeEvent(session.getId(), WorkbenchSessionEventType.TOOL_CALL,
                    runtimePayloadWithRunContext(redactedRuntimePayload(event.payloadJson()), runId, sourceUserEventId, sourceUserContent));
        } else if (event.type() == AgentRuntimeEventType.APPROVAL_REQUIRED) {
            WorkbenchSessionEvent savedEvent = appendRuntimeEvent(
                    session.getId(),
                    WorkbenchSessionEventType.APPROVAL_REQUIRED,
                    runtimePayloadWithRunContext(redactedRuntimePayload(event.payloadJson()), runId, sourceUserEventId, sourceUserContent));
            WorkbenchToolApproval approval = createApprovalFromRuntimeEvent(
                    session.getId(), savedEvent, event.payloadJson());
            if (approval.getStatus().name().equals("PENDING")) {
                session.waitForApproval(clock);
                sessionRepository.save(session);
                statusEvent(session, "runtime.approval_required");
            } else {
                failSession(session, "runtime.approval_denied", "Tool call denied by workbench policy", runId);
            }
        } else if (event.type() == AgentRuntimeEventType.ERROR) {
            appendRuntimeEvent(session.getId(), WorkbenchSessionEventType.ERROR,
                    runtimePayloadWithRunContext(redactedRuntimeErrorPayload(event.payloadJson()), runId, sourceUserEventId, sourceUserContent));
        }
    }

    private void writeRuntimeFile(WorkbenchSession session, AgentRuntimeEvent event) {
        String path = normalizePublicPath(event.filePath());
        byte[] content = event.fileContent();
        validatePackageFile(session, path, content);
        workspaceStoragePort.write(session.getWorkspaceKey(), path, content, event.contentType());
        replaceSnapshot(session, WorkbenchFileSnapshotType.CURRENT, path, content, event.contentType());
        appendRuntimeEvent(session.getId(), WorkbenchSessionEventType.FILE_CHANGED,
                json(Map.of("action", "file.written", "path", path, "source", "runtime")));
    }

    private void deleteRuntimeFile(WorkbenchSession session, AgentRuntimeEvent event) {
        String path = normalizePublicPath(event.filePath());
        workspaceStoragePort.delete(session.getWorkspaceKey(), path);
        fileSnapshotRepository.deleteBySessionIdAndTypeAndFilePath(
                session.getId(), WorkbenchFileSnapshotType.CURRENT, path);
        appendRuntimeEvent(session.getId(), WorkbenchSessionEventType.FILE_CHANGED,
                json(Map.of("action", "file.deleted", "path", path, "source", "runtime")));
    }

    private PackageBuildResult buildPackage(WorkbenchSession session) {
        List<WorkbenchFileSnapshot> snapshots = fileSnapshotRepository
                .findBySessionIdAndType(session.getId(), WorkbenchFileSnapshotType.CURRENT)
                .stream()
                .sorted(Comparator.comparing(WorkbenchFileSnapshot::getFilePath))
                .toList();
        List<PackageEntry> entries = new ArrayList<>();
        List<WorkbenchPackageFile> included = new ArrayList<>();
        List<WorkbenchPackageExcludedFile> excluded = new ArrayList<>();
        long totalSize = 0L;
        for (WorkbenchFileSnapshot snapshot : snapshots) {
            String path = snapshot.getFilePath();
            if (isWorkbenchRuntimeArtifact(path)) {
                excluded.add(new WorkbenchPackageExcludedFile(path, RUNTIME_ARTIFACT_REASON));
                continue;
            }
            byte[] content = workspaceStoragePort.read(session.getWorkspaceKey(), snapshot.getContentStorageKey())
                    .orElseThrow(() -> new IllegalStateException("workspace file content missing: " + path));
            content = contentForPublish(session, path, content);
            String contentType = snapshot.getContentType() == null || snapshot.getContentType().isBlank()
                    ? "application/octet-stream"
                    : snapshot.getContentType();
            entries.add(new PackageEntry(path, content, content.length, contentType));
            included.add(new WorkbenchPackageFile(path, content.length, sha256(content)));
            totalSize += content.length;
        }
        return new PackageBuildResult(
                entries,
                included,
                excluded,
                packageFingerprint(entries),
                totalSize);
    }

    private WorkbenchPackageValidationReport validationReport(WorkbenchPublishValidationResult validation) {
        List<String> errors = validation.errors() == null ? List.of() : List.copyOf(validation.errors());
        List<String> warnings = validation.warnings() == null ? List.of() : List.copyOf(validation.warnings());
        List<String> messages = new ArrayList<>(errors);
        messages.addAll(warnings);
        return new WorkbenchPackageValidationReport(
                validation.valid() ? "PASS" : "FAIL",
                List.copyOf(messages),
                errors,
                warnings,
                validation.resolvedSlug(),
                validation.resolvedVersion());
    }

    private WorkbenchPublishValidationResult validateResolvedTarget(
            WorkbenchSession session,
            WorkbenchPublishValidationResult validation) {
        List<String> errors = new ArrayList<>(validation.errors() == null ? List.of() : validation.errors());
        List<String> warnings = validation.warnings() == null ? List.of() : validation.warnings();
        if (validation.resolvedSlug() != null
                && !validation.resolvedSlug().isBlank()
                && !session.getTargetSlug().equals(validation.resolvedSlug())) {
            errors.add("Resolved skill slug does not match workbench target slug: "
                    + validation.resolvedSlug() + " != " + session.getTargetSlug());
        }
        if (validation.resolvedVersion() != null
                && !validation.resolvedVersion().isBlank()
                && !session.getTargetVersion().equals(validation.resolvedVersion())) {
            errors.add("Resolved skill version does not match workbench target version: "
                    + validation.resolvedVersion() + " != " + session.getTargetVersion());
        }
        return new WorkbenchPublishValidationResult(
                validation.valid() && errors.isEmpty(),
                List.copyOf(errors),
                List.copyOf(warnings),
                validation.resolvedSlug(),
                validation.resolvedVersion());
    }

    private static String validationReportJson(WorkbenchPackageValidationReport report, SkillVisibility visibility) {
        try {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("status", report.status());
            values.put("messages", report.messages());
            values.put("errors", report.errors());
            values.put("warnings", report.warnings());
            values.put("resolvedSlug", report.resolvedSlug());
            values.put("resolvedVersion", report.resolvedVersion());
            values.put("visibility", visibility.name());
            return JSON.writeValueAsString(values);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize workbench validation report", ex);
        }
    }

    private static boolean isWorkbenchRuntimeArtifact(String path) {
        String normalized = path.replace('\\', '/');
        return normalized.equals("AGENTS.md")
                || normalized.equals("session.log")
                || normalized.endsWith(".log")
                || normalized.startsWith(".baseline/")
                || normalized.startsWith(".workbench/")
                || normalized.startsWith(".agentscope/")
                || normalized.startsWith(".codex/")
                || normalized.startsWith(".claude/")
                || normalized.startsWith(".runtime/")
                || normalized.startsWith("runtime/")
                || normalized.startsWith("logs/")
                || normalized.startsWith(".logs/");
    }

    private static byte[] contentForPublish(WorkbenchSession session, String path, byte[] content) {
        if (!"SKILL.md".equals(path)) {
            return content;
        }
        String text = new String(content, StandardCharsets.UTF_8);
        String rewritten = rewriteSkillMdVersion(text, session.getTargetVersion());
        return rewritten.getBytes(StandardCharsets.UTF_8);
    }

    private static String rewriteSkillMdVersion(String text, String targetVersion) {
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        if (!normalized.startsWith("---\n")) {
            return text;
        }
        int end = normalized.indexOf("\n---", 4);
        if (end < 0) {
            return text;
        }
        String frontmatter = normalized.substring(4, end);
        String closingDelimiterAndBody = normalized.substring(end + 1);
        String[] lines = frontmatter.split("\n", -1);
        StringBuilder builder = new StringBuilder();
        boolean replaced = false;
        for (String line : lines) {
            if (line.matches("\\s*version\\s*:.*")) {
                builder.append("version: ").append(targetVersion).append('\n');
                replaced = true;
            } else {
                builder.append(line).append('\n');
            }
        }
        if (!replaced) {
            builder.append("version: ").append(targetVersion).append('\n');
        }
        return "---\n" + builder + closingDelimiterAndBody;
    }

    private static String runtimePayloadWithRunId(String payloadJson, String runId) {
        return runtimePayloadWithRunContext(payloadJson, runId, null, null);
    }

    private static String runtimePayloadWithRunContext(
            String payloadJson,
            String runId,
            Long sourceUserEventId,
            String sourceUserContent) {
        if ((runId == null || runId.isBlank())
                && sourceUserEventId == null
                && (sourceUserContent == null || sourceUserContent.isBlank())) {
            return payloadJson;
        }
        ObjectNode node = readObject(payloadJson).deepCopy();
        if (runId != null && !runId.isBlank()) {
            node.put("runId", runId);
        }
        if (sourceUserEventId != null) {
            node.put("sourceUserEventId", sourceUserEventId);
        }
        if (sourceUserContent != null && !sourceUserContent.isBlank()) {
            node.put("sourceUserContent", sourceUserContent);
        }
        try {
            return JSON.writeValueAsString(node);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize runtime event payload", ex);
        }
    }

    private WorkbenchSessionEvent appendRuntimeEvent(Long sessionId, WorkbenchSessionEventType eventType, String payloadJson) {
        return eventRepository.append(new WorkbenchSessionEvent(sessionId, eventType, payloadJson, clock));
    }

    private WorkbenchToolApproval decideApproval(Long sessionId, Long approvalId, String userId, boolean approved) {
        WorkbenchSession session = loadAuthorizedSession(sessionId, userId);
        WorkbenchToolApproval approval = toolApprovalRepository.findByIdAndSessionId(approvalId, session.getId())
                .orElseThrow(() -> new IllegalArgumentException("workbench tool approval not found: " + approvalId));
        if (approved) {
            approval.approve(userId, clock);
        } else {
            approval.reject(userId, clock);
        }
        WorkbenchToolApproval saved = toolApprovalRepository.save(approval);
        appendRuntimeEvent(session.getId(), WorkbenchSessionEventType.APPROVAL_DECIDED,
                json(Map.of(
                        "approvalId", saved.getId(),
                        "decision", saved.getStatus().name(),
                        "toolName", saved.getToolName())));
        if (approved && session.getStatus() == WorkbenchSessionStatus.WAITING_APPROVAL) {
            session.resumeFromApproval(clock);
            sessionRepository.save(session);
            statusEvent(session, "runtime.approval_approved");
        }
        if (!approved && session.getStatus() == WorkbenchSessionStatus.WAITING_APPROVAL) {
            failSession(session, "runtime.approval_rejected", "Tool call rejected by user",
                    runtimeRunIdForApproval(session, saved));
        } else if (!approved) {
            statusEvent(session, "runtime.approval_rejected");
        }
        return saved;
    }

    private WorkbenchToolApproval createApprovalFromRuntimeEvent(Long sessionId, WorkbenchSessionEvent event, String payloadJson) {
        if (toolApprovalRepository.findByEventId(event.getId()).isPresent()) {
            return toolApprovalRepository.findByEventId(event.getId()).orElseThrow();
        }
        JsonNode payload = readObject(payloadJson);
        String toolName = firstText(payload, "toolName", "tool_name", "name", "tool");
        if (toolName == null) {
            toolName = "unknown";
        }
        String mcpServerId = firstText(payload, "mcpServerId", "mcp_server_id", "serverId", "server_id");
        WorkbenchToolRiskLevel riskLevel = riskLevel(firstText(payload, "risk", "riskLevel", "risk_level"));
        String arguments = redactedArguments(payload.get("arguments"));
        return toolApprovalRepository.save(new WorkbenchToolApproval(
                sessionId,
                event.getId(),
                toolName,
                mcpServerId,
                riskLevel,
                arguments,
                clock));
    }

    private Map<String, WorkbenchMcpRuntimeCandidate> runtimeCandidatesByServer(Set<String> selectedServerIds) {
        if (selectedServerIds.isEmpty()) {
            return Map.of();
        }
        for (String serverId : selectedServerIds) {
            if (serverId.length() > 128 || !serverId.matches("[A-Za-z0-9._:-]+")) {
                throw new IllegalArgumentException("mcp server id is invalid: " + serverId);
            }
        }
        Map<String, WorkbenchMcpRuntimeCandidate> candidatesByServer = new LinkedHashMap<>();
        for (WorkbenchMcpRuntimeCandidate candidate : mcpCatalogPort.resolveRuntimeCandidates(List.copyOf(selectedServerIds))) {
            candidatesByServer.put(candidate.serverId(), candidate);
        }
        for (String serverId : selectedServerIds) {
            if (!candidatesByServer.containsKey(serverId)) {
                throw new SecurityException("mcp server is unavailable for workbench session: " + serverId);
            }
        }
        return candidatesByServer;
    }

    private List<AgentRuntimeMcpServer> runtimeMcpServers(Long sessionId) {
        List<WorkbenchMcpBinding> bindings = mcpBindingRepository.findBySessionIdAndStatus(
                sessionId, WorkbenchMcpBindingStatus.ENABLED);
        if (bindings.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> serverIds = bindings.stream()
                .map(WorkbenchMcpBinding::getMcpServerId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Map<String, WorkbenchMcpRuntimeCandidate> candidates = runtimeCandidatesByServer(serverIds);
        return bindings.stream()
                .map(binding -> runtimeMcpServer(binding, candidates.get(binding.getMcpServerId())))
                .toList();
    }

    private AgentRuntimeMcpServer runtimeMcpServer(
            WorkbenchMcpBinding binding,
            WorkbenchMcpRuntimeCandidate candidate) {
        String transport;
        String url;
        if (candidate.streamableHttpUrl() != null && !candidate.streamableHttpUrl().isBlank()) {
            transport = "http";
            url = candidate.streamableHttpUrl();
        } else if (candidate.sseUrl() != null && !candidate.sseUrl().isBlank()) {
            transport = "sse";
            url = candidate.sseUrl();
        } else {
            throw new IllegalStateException("MCP server has no runtime endpoint: " + binding.getMcpServerId());
        }
        return new AgentRuntimeMcpServer(
                binding.getMcpServerId(),
                transport,
                url,
                stringArray(binding.getEnabledToolsJson()),
                stringArray(binding.getDisabledToolsJson()));
    }

    private List<AgentRuntimeWorkspaceFile> runtimeWorkspaceFiles(WorkbenchSession session) {
        return fileSnapshotRepository.findBySessionIdAndType(session.getId(), WorkbenchFileSnapshotType.CURRENT)
                .stream()
                .sorted(Comparator.comparing(WorkbenchFileSnapshot::getFilePath))
                .map(snapshot -> new AgentRuntimeWorkspaceFile(
                        snapshot.getFilePath(),
                        workspaceStoragePort.read(session.getWorkspaceKey(), snapshot.getContentStorageKey())
                                .orElseThrow(() -> new IllegalStateException(
                                        "workspace file content missing: " + snapshot.getFilePath())),
                        snapshot.getContentType()))
                .toList();
    }

    private static List<String> stringArray(String value) {
        JsonNode node = readObjectOrArray(value);
        if (!node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(item -> {
            if (item.isTextual() && !item.textValue().isBlank()) {
                values.add(item.textValue().trim());
            }
        });
        return List.copyOf(values);
    }

    private static JsonNode readObjectOrArray(String value) {
        try {
            return JSON.readTree(value == null || value.isBlank() ? "[]" : value);
        } catch (JsonProcessingException ex) {
            return JSON.createArrayNode();
        }
    }

    private LinkedHashSet<String> selectedServerIds(WorkbenchMcpBindingUpdateCommand command) {
        LinkedHashSet<String> serverIds = new LinkedHashSet<>();
        if (command != null && command.serverIds() != null) {
            command.serverIds().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .forEach(serverIds::add);
        }
        if (command != null && command.bindings() != null) {
            command.bindings().stream()
                    .filter(Objects::nonNull)
                    .map(WorkbenchMcpBindingSelection::serverId)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .forEach(serverIds::add);
        }
        return serverIds;
    }

    private Map<String, WorkbenchMcpBindingSelection> selectionsByServer(WorkbenchMcpBindingUpdateCommand command) {
        if (command == null || command.bindings() == null) {
            return Map.of();
        }
        Map<String, WorkbenchMcpBindingSelection> selections = new LinkedHashMap<>();
        command.bindings().stream()
                .filter(Objects::nonNull)
                .filter(selection -> selection.serverId() != null && !selection.serverId().isBlank())
                .forEach(selection -> selections.put(selection.serverId().trim(), selection));
        return Map.copyOf(selections);
    }

    private static JsonNode readObject(String payloadJson) {
        try {
            JsonNode node = JSON.readTree(payloadJson == null || payloadJson.isBlank() ? "{}" : payloadJson);
            return node != null && node.isObject() ? node : JSON.createObjectNode();
        } catch (JsonProcessingException ex) {
            return JSON.createObjectNode();
        }
    }

    private String runtimeRunIdForApproval(WorkbenchSession session, WorkbenchToolApproval approval) {
        return eventRepository.findBySessionIdAndEventId(session.getId(), approval.getEventId())
                .filter(event -> event.getEventType() == WorkbenchSessionEventType.APPROVAL_REQUIRED)
                .map(WorkbenchSessionEvent::getPayloadJson)
                .map(WorkbenchSessionApplicationService::payloadRunId)
                .orElse(null);
    }

    private static String payloadRunId(String payloadJson) {
        JsonNode runId = readObject(payloadJson).get("runId");
        return runId != null && runId.isTextual() && !runId.asText().isBlank() ? runId.asText() : null;
    }

    private static String firstText(JsonNode node, String... names) {
        if (node == null || !node.isObject()) {
            return null;
        }
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && value.isTextual() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return null;
    }

    private static WorkbenchToolRiskLevel riskLevel(String value) {
        if (value == null || value.isBlank()) {
            return WorkbenchToolRiskLevel.UNKNOWN;
        }
        try {
            return WorkbenchToolRiskLevel.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return WorkbenchToolRiskLevel.UNKNOWN;
        }
    }

    private static String redactedArguments(JsonNode arguments) {
        JsonNode redacted = arguments != null && arguments.isObject()
                ? redactObject(arguments)
                : JSON.createObjectNode();
        try {
            return JSON.writeValueAsString(redacted);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private static JsonNode redactObject(JsonNode arguments) {
        ObjectNode object = JSON.createObjectNode();
        arguments.fieldNames().forEachRemaining(fieldName -> object.put(fieldName, "[REDACTED]"));
        return object;
    }

    private static String redactedRuntimePayload(String payloadJson) {
        try {
            JsonNode redacted = redactRuntimeNode(readObject(payloadJson), null, false);
            return JSON.writeValueAsString(redacted);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private static String redactedRuntimeErrorPayload(String payloadJson) {
        try {
            JsonNode redacted = redactRuntimeNode(readObject(payloadJson), null, true);
            return JSON.writeValueAsString(redacted);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private static JsonNode redactRuntimeNode(JsonNode node, String fieldName, boolean sanitizeTextLeaves) {
        if (node == null || node.isNull()) {
            return JSON.nullNode();
        }
        if (fieldName != null && (isSensitiveField(fieldName) || fieldName.equalsIgnoreCase("arguments")
                || fieldName.equalsIgnoreCase("args"))) {
            return JSON.getNodeFactory().textNode("[REDACTED]");
        }
        if (node.isObject()) {
            ObjectNode object = JSON.createObjectNode();
            node.fields().forEachRemaining(entry -> object.set(
                    entry.getKey(),
                    redactRuntimeNode(entry.getValue(), entry.getKey(), sanitizeTextLeaves)));
            return object;
        }
        if (node.isArray()) {
            ArrayNode array = JSON.createArrayNode();
            node.forEach(item -> array.add(redactRuntimeNode(item, fieldName, sanitizeTextLeaves)));
            return array;
        }
        if (sanitizeTextLeaves && node.isTextual()) {
            return JSON.getNodeFactory().textNode(sanitizeRuntimeMessage(node.asText()));
        }
        return node.deepCopy();
    }

    private static boolean isSensitiveField(String fieldName) {
        String normalized = fieldName.toLowerCase();
        return normalized.contains("token")
                || normalized.contains("password")
                || normalized.contains("secret")
                || normalized.contains("credential")
                || normalized.contains("authorization")
                || normalized.contains("cookie")
                || normalized.contains("header")
                || normalized.contains("endpoint")
                || normalized.endsWith("key")
                || normalized.endsWith("url")
                || normalized.endsWith("ref");
    }

    private static String sanitizeRuntimeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        return message
                .replaceAll("(?i)(token|password|secret|credential|authorization|cookie|api[-_ ]?key)\\s*[:=]\\s*(?:bearer\\s+)?[^\\s,;]+", "$1=[REDACTED]")
                .replaceAll("(?i)https?://[^\\s,;]+", "[REDACTED_URL]");
    }

    private void failSession(WorkbenchSession session, String action, String message) {
        failSession(session, action, message, null);
    }

    private void failSession(WorkbenchSession session, String action, String message, String runId) {
        if (!session.isTerminal()) {
            session.fail(clock);
            sessionRepository.save(session);
        }
        appendRuntimeEvent(session.getId(), WorkbenchSessionEventType.ERROR,
                runtimePayloadWithRunId(json(Map.of("action", action, "message", sanitizeRuntimeMessage(message))), runId));
        statusEvent(session, action);
    }

    private void replaceSnapshot(WorkbenchSession session, WorkbenchFileSnapshotType type,
                                 String path, byte[] content, String contentType) {
        fileSnapshotRepository.deleteBySessionIdAndTypeAndFilePath(session.getId(), type, path);
        fileSnapshotRepository.save(new WorkbenchFileSnapshot(
                session.getId(), type, path, storageKey(session, type, path, sha256(content)),
                sha256(content), (long) content.length, contentType, session.getExpiresAt(), clock));
    }

    private Map<String, WorkbenchFileSnapshot> snapshotsByPath(Long sessionId, WorkbenchFileSnapshotType type) {
        Map<String, WorkbenchFileSnapshot> snapshots = new LinkedHashMap<>();
        for (WorkbenchFileSnapshot snapshot : fileSnapshotRepository.findBySessionIdAndType(sessionId, type)) {
            snapshots.put(snapshot.getFilePath(), snapshot);
        }
        return snapshots;
    }

    private WorkbenchFileDiffStatus statusFor(WorkbenchFileSnapshot baseline, WorkbenchFileSnapshot current) {
        if (baseline == null) {
            return WorkbenchFileDiffStatus.ADDED;
        }
        if (current == null) {
            return WorkbenchFileDiffStatus.DELETED;
        }
        if (baseline.getSha256().equals(current.getSha256())) {
            return WorkbenchFileDiffStatus.UNCHANGED;
        }
        return WorkbenchFileDiffStatus.MODIFIED;
    }

    private void statusEvent(WorkbenchSession session, String action) {
        eventRepository.append(new WorkbenchSessionEvent(
                session.getId(),
                WorkbenchSessionEventType.STATUS_CHANGED,
                json(Map.of("action", action, "status", session.getStatus().name())),
                clock));
        audit(session.getId(), action, Map.of("status", session.getStatus().name()));
    }

    private void fileEvent(Long sessionId, String action, String path) {
        eventRepository.append(new WorkbenchSessionEvent(
                sessionId,
                WorkbenchSessionEventType.FILE_CHANGED,
                json(Map.of("action", action, "path", path)),
                clock));
        audit(sessionId, action, Map.of("path", path));
    }

    private void audit(Long sessionId, String action, Map<String, ?> fields) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", action);
        payload.putAll(fields);
        eventRepository.append(new WorkbenchSessionEvent(
                sessionId, WorkbenchSessionEventType.AUDIT, json(payload), clock));
    }

    private String storageKey(WorkbenchSession session, WorkbenchFileSnapshotType type, String path, String sha256) {
        if (type == WorkbenchFileSnapshotType.CURRENT) {
            return path;
        }
        return baselineStorageKey(session, path, sha256);
    }

    private String baselineStorageKey(WorkbenchSession session, String path, String sha256) {
        return ".workbench/baselines/" + session.getId() + "/" + sha256 + "/" + path;
    }

    private String workspaceKey(CreateWorkbenchSessionCommand command) {
        String seed = command.userId() + "/" + command.namespaceId() + "/" + command.mode() + "/"
                + command.targetSlug() + "/" + clock.instant();
        return "workbench/" + command.userId() + "/" + sha256(seed.getBytes(StandardCharsets.UTF_8)).substring(0, 16);
    }

    private String normalize(String filePath) {
        return WorkbenchFileSnapshot.normalizeFilePath(filePath);
    }

    private String normalizePublicPath(String filePath) {
        String path = normalize(filePath);
        if (path.equals("AGENTS.md")
                || path.startsWith(".baseline/")
                || path.startsWith(".workbench/")
                || path.startsWith(".agentscope/")) {
            throw new IllegalArgumentException("filePath uses a reserved workbench path");
        }
        return path;
    }

    private void validatePackageFile(WorkbenchSession session, String path, byte[] content) {
        if (!SkillPackagePolicy.hasAllowedExtension(path)) {
            throw new IllegalArgumentException("filePath uses a disallowed package extension");
        }
        if (content.length > SkillPackagePolicy.MAX_SINGLE_FILE_SIZE) {
            throw new IllegalArgumentException("workspace file exceeds max single file size");
        }
        String contentMismatch = SkillPackagePolicy.validateContentMatchesExtension(path, content);
        if (contentMismatch != null) {
            throw new IllegalArgumentException(contentMismatch);
        }
        long totalSize = content.length;
        int fileCount = 1;
        for (WorkbenchFileSnapshot snapshot : fileSnapshotRepository.findBySessionIdAndType(
                session.getId(), WorkbenchFileSnapshotType.CURRENT)) {
            if (snapshot.getFilePath().equals(path)) {
                continue;
            }
            fileCount++;
            totalSize += snapshot.getSizeBytes();
        }
        if (fileCount > SkillPackagePolicy.MAX_FILE_COUNT) {
            throw new IllegalArgumentException("workspace file count exceeds package policy");
        }
        if (totalSize > SkillPackagePolicy.MAX_TOTAL_PACKAGE_SIZE) {
            throw new IllegalArgumentException("workspace total size exceeds package policy");
        }
    }

    private static String sha256(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static String packageFingerprint(List<PackageEntry> entries) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (PackageEntry entry : entries.stream().sorted(Comparator.comparing(PackageEntry::path)).toList()) {
                digest.update(entry.path().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(sha256(entry.content()).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(Long.toString(entry.size()).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(String.valueOf(entry.contentType()).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            byte[] fingerprint = digest.digest();
            StringBuilder builder = new StringBuilder("sha256:");
            for (byte value : fingerprint) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private record PackageBuildResult(
            List<PackageEntry> entries,
            List<WorkbenchPackageFile> includedFiles,
            List<WorkbenchPackageExcludedFile> excludedFiles,
            String fingerprint,
            long totalSize) {
    }

    private static String json(Map<String, ?> values) {
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('"').append(escape(entry.getKey())).append('"').append(':');
            Object value = entry.getValue();
            if (value instanceof Number || value instanceof Boolean) {
                builder.append(value);
            } else {
                builder.append('"').append(escape(String.valueOf(value))).append('"');
            }
        }
        return builder.append('}').toString();
    }

    private static String jsonArray(List<String> values) {
        try {
            return JSON.writeValueAsString(values == null ? List.of() : values.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(String::trim)
                    .distinct()
                    .toList());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize tool selection", ex);
        }
    }

    private static String escape(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (ch == '\\') {
                builder.append("\\\\");
            } else if (ch == '"') {
                builder.append("\\\"");
            } else if (ch == '\n') {
                builder.append("\\n");
            } else if (ch == '\r') {
                builder.append("\\r");
            } else if (ch == '\t') {
                builder.append("\\t");
            } else if (ch < 0x20) {
                builder.append(String.format("\\u%04x", (int) ch));
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }
}
