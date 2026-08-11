package com.iflytek.skillhub.workbench.application;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.domain.skill.validation.SkillPackagePolicy;
import com.iflytek.skillhub.workbench.domain.WorkbenchFileSnapshot;
import com.iflytek.skillhub.workbench.domain.WorkbenchFileSnapshotType;
import com.iflytek.skillhub.workbench.domain.WorkbenchMode;
import com.iflytek.skillhub.workbench.domain.WorkbenchMcpBinding;
import com.iflytek.skillhub.workbench.domain.WorkbenchMcpBindingStatus;
import com.iflytek.skillhub.workbench.domain.WorkbenchMcpCatalogSource;
import com.iflytek.skillhub.workbench.domain.WorkbenchPublishCandidate;
import com.iflytek.skillhub.workbench.domain.WorkbenchPublishValidationStatus;
import com.iflytek.skillhub.workbench.domain.WorkbenchSession;
import com.iflytek.skillhub.workbench.domain.WorkbenchSessionEvent;
import com.iflytek.skillhub.workbench.domain.WorkbenchSessionEventType;
import com.iflytek.skillhub.workbench.domain.WorkbenchSessionStatus;
import com.iflytek.skillhub.workbench.domain.WorkbenchToolApproval;
import com.iflytek.skillhub.workbench.domain.WorkbenchToolApprovalStatus;
import com.iflytek.skillhub.workbench.domain.WorkbenchToolRiskLevel;
import com.iflytek.skillhub.workbench.port.AgentRuntimeCancelCommand;
import com.iflytek.skillhub.workbench.port.AgentRuntimeEvent;
import com.iflytek.skillhub.workbench.port.AgentRuntimeEventType;
import com.iflytek.skillhub.workbench.port.AgentRuntimePort;
import com.iflytek.skillhub.workbench.port.AgentRuntimeRunCommand;
import com.iflytek.skillhub.workbench.port.AgentRuntimeRunHandle;
import com.iflytek.skillhub.workbench.port.AgentRuntimeRunStatus;
import com.iflytek.skillhub.workbench.port.AgentRuntimeStreamListener;
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
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkbenchSessionApplicationServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-30T00:00:00Z"), ZoneOffset.UTC);

    private InMemorySessionRepository sessions;
    private InMemoryEventRepository events;
    private InMemoryFileSnapshotRepository snapshots;
    private InMemoryMcpBindingRepository mcpBindings;
    private FakeMcpCatalog mcpCatalog;
    private InMemoryToolApprovalRepository approvals;
    private InMemoryPublishCandidateRepository candidates;
    private FakeSkillPublisher publisher;
    private InMemorySkillSource source;
    private InMemoryWorkspaceStorage storage;
    private FakeAuthorization authorization;
    private FakeRuntime runtime;
    private WorkbenchSessionApplicationService service;

    @BeforeEach
    void setUp() {
        sessions = new InMemorySessionRepository();
        events = new InMemoryEventRepository();
        snapshots = new InMemoryFileSnapshotRepository();
        mcpBindings = new InMemoryMcpBindingRepository();
        mcpCatalog = new FakeMcpCatalog();
        approvals = new InMemoryToolApprovalRepository();
        candidates = new InMemoryPublishCandidateRepository();
        publisher = new FakeSkillPublisher();
        source = new InMemorySkillSource();
        storage = new InMemoryWorkspaceStorage();
        authorization = new FakeAuthorization();
        runtime = new FakeRuntime();
        service = new WorkbenchSessionApplicationService(
                sessions, events, snapshots, mcpBindings, mcpCatalog, approvals, candidates, publisher,
                source, storage, authorization, runtime, CLOCK);
    }

    private WorkbenchSessionApplicationService newService(AgentRuntimePort runtimePort) {
        return new WorkbenchSessionApplicationService(
                sessions, events, snapshots, mcpBindings, mcpCatalog, approvals, candidates, publisher,
                source, storage, authorization, runtimePort, CLOCK);
    }

    @Test
    void createSessionRejectsUsersWithoutNamespacePermission() {
        authorization.denyCreate = true;

        assertThatThrownBy(() -> service.createSession(new CreateWorkbenchSessionCommand(
                "user-1", 10L, WorkbenchMode.CREATE_SKILL, null, null,
                "new-skill", "1.0.0", Instant.parse("2026-08-06T00:00:00Z"))))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("namespace");

        assertThat(sessions.rows).isEmpty();
        assertThat(events.rows).isEmpty();
    }

    @Test
    void updateSessionRejectsUsersWithoutSourceVersionPermissionBeforeSavingDraft() {
        authorization.denyUpdateSource = true;

        assertThatThrownBy(() -> createUpdateSession())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("source version");

        assertThat(sessions.rows).isEmpty();
        assertThat(events.rows).isEmpty();
    }

    @Test
    void updateSessionImportsSourceFilesToWorkspaceAndCreatesImmutableBaseline() {
        source.put(200L, List.of(
                new SourceFixture("SKILL.md", "name: old\n", "text/markdown"),
                new SourceFixture("references/guide.md", "guide", "text/markdown")));

        WorkbenchSession session = createUpdateSession();

        ImportWorkbenchSourceResult result = service.importSourceVersion(session.getId(), "user-1");

        assertThat(result.importedFiles()).containsExactly("SKILL.md", "references/guide.md");
        assertThat(storage.read(session.getWorkspaceKey(), "SKILL.md")).contains("name: old\n".getBytes(StandardCharsets.UTF_8));
        assertThat(snapshots.findBySessionIdAndType(session.getId(), WorkbenchFileSnapshotType.BASELINE))
                .extracting(WorkbenchFileSnapshot::getFilePath)
                .containsExactly("SKILL.md", "references/guide.md");

        source.put(200L, List.of(new SourceFixture("SKILL.md", "name: changed\n", "text/markdown")));
        service.importSourceVersion(session.getId(), "user-1");

        WorkbenchFileSnapshot baseline = snapshots.findBySessionIdAndTypeAndFilePath(
                session.getId(), WorkbenchFileSnapshotType.BASELINE, "SKILL.md").orElseThrow();
        assertThat(storage.read(session.getWorkspaceKey(), baseline.getContentStorageKey()))
                .contains("name: old\n".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void importRequiresUpdateSkillSession() {
        WorkbenchSession session = service.createSession(new CreateWorkbenchSessionCommand(
                "user-1", 10L, WorkbenchMode.CREATE_SKILL, null, null,
                "new-skill", "1.0.0", Instant.parse("2026-08-06T00:00:00Z")));

        assertThatThrownBy(() -> service.importSourceVersion(session.getId(), "user-1"))
                .isInstanceOf(DomainBadRequestException.class)
                .hasMessage("error.workbench.import.updateSkillRequired");
    }

    @Test
    void importRejectsReservedSourcePathsBeforeWritingWorkspaceState() {
        source.put(200L, List.of(new SourceFixture(".workbench/state.json", "internal", "application/json")));
        WorkbenchSession session = createUpdateSession();

        assertThatThrownBy(() -> service.importSourceVersion(session.getId(), "user-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reserved");

        assertThat(storage.objects).isEmpty();
        assertThat(snapshots.findBySessionIdAndType(session.getId(), WorkbenchFileSnapshotType.CURRENT)).isEmpty();
        assertThat(snapshots.findBySessionIdAndType(session.getId(), WorkbenchFileSnapshotType.BASELINE)).isEmpty();
        assertThat(events.rows)
                .extracting(WorkbenchSessionEvent::getPayloadJson)
                .noneMatch(payload -> payload.contains("source.imported"));
    }

    @Test
    void importRejectsDisallowedPackageFilesBeforeWritingWorkspaceState() {
        source.put(200L, List.of(new SourceFixture("tool.exe", "binary", "application/octet-stream")));
        WorkbenchSession session = createUpdateSession();

        assertThatThrownBy(() -> service.importSourceVersion(session.getId(), "user-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("disallowed");

        assertThat(storage.objects).isEmpty();
        assertThat(snapshots.findBySessionIdAndType(session.getId(), WorkbenchFileSnapshotType.CURRENT)).isEmpty();
        assertThat(events.rows)
                .extracting(WorkbenchSessionEvent::getPayloadJson)
                .noneMatch(payload -> payload.contains("source.imported"));
    }

    @Test
    void fileOperationsRejectNonNormalizedPaths() {
        WorkbenchSession session = createUpdateSession();

        assertThatThrownBy(() -> service.writeFile(session.getId(), "user-1", "../SKILL.md",
                "bad".getBytes(StandardCharsets.UTF_8), "text/markdown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("relative workspace path");

        assertThatThrownBy(() -> service.readFile(session.getId(), "user-1", "dir/../SKILL.md"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("relative workspace path");
    }

    @Test
    void fileOperationsRejectReservedWorkbenchPaths() {
        WorkbenchSession session = createUpdateSession();
        String[] reservedPaths = {
                ".baseline/SKILL.md",
                ".baseline/nested/SKILL.md",
                ".workbench/state.json",
                ".agentscope/session.json",
                "AGENTS.md"
        };

        for (String reservedPath : reservedPaths) {
            assertThatThrownBy(() -> service.writeFile(session.getId(), "user-1", reservedPath,
                    "bad".getBytes(StandardCharsets.UTF_8), "text/markdown"))
                    .as(reservedPath)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("reserved");
            assertThatThrownBy(() -> service.readFile(session.getId(), "user-1", reservedPath))
                    .as(reservedPath)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("reserved");
            assertThatThrownBy(() -> service.deleteFile(session.getId(), "user-1", reservedPath))
                    .as(reservedPath)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("reserved");
        }
    }

    @Test
    void writeFileRejectsDisallowedExtensionsAndOversizedContentBeforeStorageWrite() {
        WorkbenchSession session = createUpdateSession();

        assertThatThrownBy(() -> service.writeFile(session.getId(), "user-1", "tool.exe",
                "bad".getBytes(StandardCharsets.UTF_8), "application/octet-stream"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("disallowed");

        assertThatThrownBy(() -> service.writeFile(session.getId(), "user-1", "large.md",
                new byte[(int) SkillPackagePolicy.MAX_SINGLE_FILE_SIZE + 1], "text/markdown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("single file size");

        assertThat(storage.objects).isEmpty();
        assertThat(snapshots.findBySessionIdAndType(session.getId(), WorkbenchFileSnapshotType.CURRENT)).isEmpty();
    }

    @Test
    void currentSnapshotContentStorageKeyCanReadCurrentContent() {
        WorkbenchSession session = createUpdateSession();

        service.writeFile(session.getId(), "user-1", "SKILL.md",
                "current".getBytes(StandardCharsets.UTF_8), "text/markdown");

        WorkbenchFileSnapshot current = snapshots.findBySessionIdAndTypeAndFilePath(
                session.getId(), WorkbenchFileSnapshotType.CURRENT, "SKILL.md").orElseThrow();
        assertThat(current.getContentStorageKey()).isEqualTo("SKILL.md");
        assertThat(storage.read(session.getWorkspaceKey(), current.getContentStorageKey()))
                .contains("current".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void baselineStorageKeyIsInternalAndCannotBeChangedThroughUserFileOperations() {
        source.put(200L, List.of(new SourceFixture("SKILL.md", "baseline", "text/markdown")));
        WorkbenchSession session = createUpdateSession();
        service.importSourceVersion(session.getId(), "user-1");
        WorkbenchFileSnapshot baseline = snapshots.findBySessionIdAndTypeAndFilePath(
                session.getId(), WorkbenchFileSnapshotType.BASELINE, "SKILL.md").orElseThrow();

        assertThat(baseline.getContentStorageKey()).startsWith(".workbench/baselines/");
        assertThat(service.readFile(session.getId(), "user-1", "SKILL.md").content())
                .isEqualTo("baseline".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.writeFile(session.getId(), "user-1", baseline.getContentStorageKey(),
                "tampered".getBytes(StandardCharsets.UTF_8), "text/markdown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reserved");
        assertThatThrownBy(() -> service.deleteFile(session.getId(), "user-1", baseline.getContentStorageKey()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reserved");
        assertThat(storage.read(session.getWorkspaceKey(), baseline.getContentStorageKey()))
                .contains("baseline".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void fileListReadWriteDeleteUseSessionWorkspaceAndAuditEdits() {
        WorkbenchSession session = createUpdateSession();

        service.writeFile(session.getId(), "user-1", "docs/readme.md",
                "hello".getBytes(StandardCharsets.UTF_8), "text/markdown");
        service.writeFile(session.getId(), "user-1", "SKILL.md",
                "skill".getBytes(StandardCharsets.UTF_8), "text/markdown");

        assertThat(service.listFiles(session.getId(), "user-1"))
                .extracting(WorkbenchWorkspaceFile::path)
                .containsExactly("SKILL.md", "docs/readme.md");
        assertThat(service.readFile(session.getId(), "user-1", "docs/readme.md").content())
                .isEqualTo("hello".getBytes(StandardCharsets.UTF_8));

        service.deleteFile(session.getId(), "user-1", "docs/readme.md");

        assertThat(service.listFiles(session.getId(), "user-1"))
                .extracting(WorkbenchWorkspaceFile::path)
                .containsExactly("SKILL.md");
        assertThat(events.rows)
                .extracting(WorkbenchSessionEvent::getEventType)
                .contains(WorkbenchSessionEventType.FILE_CHANGED);
    }

    @Test
    void diffClassifiesAddedDeletedModifiedAndUnchangedFilesAgainstBaseline() {
        source.put(200L, List.of(
                new SourceFixture("SKILL.md", "old", "text/markdown"),
                new SourceFixture("deleted.md", "remove me", "text/markdown"),
                new SourceFixture("same.md", "same", "text/markdown")));
        WorkbenchSession session = createUpdateSession();
        service.importSourceVersion(session.getId(), "user-1");

        service.writeFile(session.getId(), "user-1", "SKILL.md",
                "new".getBytes(StandardCharsets.UTF_8), "text/markdown");
        service.writeFile(session.getId(), "user-1", "added.md",
                "added".getBytes(StandardCharsets.UTF_8), "text/markdown");
        service.deleteFile(session.getId(), "user-1", "deleted.md");

        assertThat(service.diff(session.getId(), "user-1").files())
                .containsExactly(
                        new WorkbenchFileDiff("SKILL.md", WorkbenchFileDiffStatus.MODIFIED),
                        new WorkbenchFileDiff("added.md", WorkbenchFileDiffStatus.ADDED),
                        new WorkbenchFileDiff("deleted.md", WorkbenchFileDiffStatus.DELETED),
                        new WorkbenchFileDiff("same.md", WorkbenchFileDiffStatus.UNCHANGED));
    }

    @Test
    void statusChangesArePersistedAndAudited() {
        WorkbenchSession session = createUpdateSession();

        WorkbenchSession updated = service.markReadyForReview(session.getId(), "user-1");

        assertThat(updated.getStatus()).isEqualTo(WorkbenchSessionStatus.READY_FOR_REVIEW);
        assertThat(events.rows)
                .extracting(WorkbenchSessionEvent::getEventType)
                .contains(WorkbenchSessionEventType.STATUS_CHANGED);
    }

    @Test
    void creationImportFileEditAndStatusChangeProduceAuditEvents() {
        source.put(200L, List.of(new SourceFixture("SKILL.md", "old", "text/markdown")));
        WorkbenchSession session = createUpdateSession();

        service.importSourceVersion(session.getId(), "user-1");
        service.writeFile(session.getId(), "user-1", "SKILL.md",
                "new".getBytes(StandardCharsets.UTF_8), "text/markdown");
        service.markReadyForReview(session.getId(), "user-1");

        assertThat(events.rows)
                .extracting(WorkbenchSessionEvent::getEventType)
                .contains(
                        WorkbenchSessionEventType.AUDIT,
                        WorkbenchSessionEventType.FILE_CHANGED,
                        WorkbenchSessionEventType.STATUS_CHANGED);
        assertThat(events.rows)
                .extracting(WorkbenchSessionEvent::getPayloadJson)
                .anyMatch(payload -> payload.contains("session.created"))
                .anyMatch(payload -> payload.contains("source.imported"))
                .anyMatch(payload -> payload.contains("file.written"))
                .anyMatch(payload -> payload.contains("status.changed"));
    }

    @Test
    void sendMessageStartsRuntimeMapsEventsAndPersistsRuntimeFileChanges() {
        WorkbenchSession session = createUpdateSession();
        runtime.nextStart = new AgentRuntimeRunHandle(
                "run-1",
                AgentRuntimeRunStatus.STARTED,
                "started",
                List.of(
                        AgentRuntimeEvent.modelMessage("done"),
                        AgentRuntimeEvent.fileWritten(
                                "SKILL.md",
                                "# Generated skill".getBytes(StandardCharsets.UTF_8),
                                "text/markdown")));

        AgentRuntimeRunHandle handle = service.sendMessage(session.getId(), "user-1", "create skill");

        assertThat(handle.runId()).isEqualTo("run-1");
        assertThat(runtime.lastStart.userId()).isEqualTo("user-1");
        assertThat(runtime.lastStart.sessionId()).isEqualTo(session.getId());
        assertThat(runtime.lastStart.workspaceKey()).isEqualTo(session.getWorkspaceKey());
        assertThat(runtime.lastStart.sessionContext())
                .contains("工作模式: 更新技能")
                .contains("目标技能标识: existing-skill")
                .contains("目标版本: 1.0.1")
                .contains("源技能 ID: 100")
                .contains("源版本 ID: 200");
        assertThat(sessions.findById(session.getId()).orElseThrow().getStatus())
                .isEqualTo(WorkbenchSessionStatus.RUNNING);
        assertThat(storage.read(session.getWorkspaceKey(), "SKILL.md"))
                .contains("# Generated skill".getBytes(StandardCharsets.UTF_8));
        assertThat(snapshots.findBySessionIdAndTypeAndFilePath(
                session.getId(), WorkbenchFileSnapshotType.CURRENT, "SKILL.md"))
                .isPresent();
        assertThat(events.rows)
                .extracting(WorkbenchSessionEvent::getEventType)
                .contains(
                        WorkbenchSessionEventType.USER_MESSAGE,
                        WorkbenchSessionEventType.MODEL_MESSAGE,
                        WorkbenchSessionEventType.FILE_CHANGED,
                        WorkbenchSessionEventType.STATUS_CHANGED);
        assertThat(events.rows)
                .filteredOn(event -> event.getEventType() == WorkbenchSessionEventType.MODEL_MESSAGE)
                .extracting(WorkbenchSessionEvent::getPayloadJson)
                .anySatisfy(payload -> assertThat(payload)
                        .contains("\"runId\":\"run-1\"")
                        .contains("\"sourceUserEventId\"")
                        .contains("\"sourceUserContent\":\"create skill\""));
    }

    @Test
    void sendMessagePassesEnabledMcpServersAndCurrentWorkspaceFilesToRuntime() {
        WorkbenchSession session = createUpdateSession();
        mcpCatalog.allow("server-a");
        service.updateMcpBindings(
                session.getId(),
                "user-1",
                new WorkbenchMcpBindingUpdateCommand(
                        null,
                        List.of(new WorkbenchMcpBindingSelection(
                                "server-a",
                                List.of("tool-a"),
                                List.of("tool-b")))));
        service.writeFile(
                session.getId(),
                "user-1",
                "SKILL.md",
                "# Current skill".getBytes(StandardCharsets.UTF_8),
                "text/markdown");

        service.sendMessage(session.getId(), "user-1", "inspect the selected MCP tools");

        assertThat(runtime.lastStart.mcpServers())
                .singleElement()
                .satisfies(server -> {
                    assertThat(server.serverId()).isEqualTo("server-a");
                    assertThat(server.transport()).isEqualTo("http");
                    assertThat(server.url()).isEqualTo("https://context-forge.example/servers/server-a/mcp");
                    assertThat(server.enabledTools()).containsExactly("tool-a");
                    assertThat(server.disabledTools()).containsExactly("tool-b");
                });
        assertThat(runtime.lastStart.workspaceFiles())
                .singleElement()
                .satisfies(file -> {
                    assertThat(file.path()).isEqualTo("SKILL.md");
                    assertThat(file.content()).isEqualTo("# Current skill".getBytes(StandardCharsets.UTF_8));
                    assertThat(file.contentType()).isEqualTo("text/markdown");
                });
    }

    @Test
    void sendMessageStreamingPersistsRuntimeEventsWithRunId() {
        WorkbenchSession session = createUpdateSession();
        runtime.nextStart = new AgentRuntimeRunHandle(
                "run-stream",
                AgentRuntimeRunStatus.STARTED,
                "streamed",
                List.of(
                        AgentRuntimeEvent.modelMessage("stream done"),
                        new AgentRuntimeEvent(
                                AgentRuntimeEventType.TOOL_CALL,
                                "{\"toolName\":\"search\",\"arguments\":{\"query\":\"public\"}}",
                                null,
                                null,
                                null),
                        AgentRuntimeEvent.error("stream warning")));

        AgentRuntimeRunHandle handle = service.sendMessageStreaming(
                session.getId(),
                "user-1",
                "stream message",
                new AgentRuntimeStreamListener() {
                });

        assertThat(handle.runId()).isEqualTo("run-stream");
        assertThat(events.rows)
                .filteredOn(event -> event.getEventType() == WorkbenchSessionEventType.MODEL_MESSAGE)
                .extracting(WorkbenchSessionEvent::getPayloadJson)
                .singleElement()
                .satisfies(payload -> assertThat(payload)
                        .contains("\"runId\":\"run-stream\"")
                        .contains("\"sourceUserEventId\"")
                        .contains("\"sourceUserContent\":\"stream message\""));
        assertThat(events.rows)
                .filteredOn(event -> event.getEventType() == WorkbenchSessionEventType.TOOL_CALL)
                .extracting(WorkbenchSessionEvent::getPayloadJson)
                .singleElement()
                .satisfies(payload -> assertThat(payload)
                        .contains("\"runId\":\"run-stream\"")
                        .contains("\"sourceUserEventId\"")
                        .contains("\"sourceUserContent\":\"stream message\""));
        assertThat(events.rows)
                .filteredOn(event -> event.getEventType() == WorkbenchSessionEventType.ERROR)
                .extracting(WorkbenchSessionEvent::getPayloadJson)
                .singleElement()
                .satisfies(payload -> assertThat(payload)
                        .contains("\"runId\":\"run-stream\"")
                        .contains("\"sourceUserEventId\"")
                        .contains("\"sourceUserContent\":\"stream message\""));
    }

    @Test
    void sendMessageEscapesControlCharactersInRuntimeEventPayloads() {
        WorkbenchSession session = createUpdateSession();
        runtime.nextStart = new AgentRuntimeRunHandle(
                "run-1",
                AgentRuntimeRunStatus.STARTED,
                "started",
                List.of(AgentRuntimeEvent.modelMessage("line1\n\"line2\"")));

        service.sendMessage(session.getId(), "user-1", "create\nskill");

        assertThat(events.rows)
                .extracting(WorkbenchSessionEvent::getPayloadJson)
                .anyMatch(payload -> payload.contains("create\\nskill"))
                .anyMatch(payload -> payload.contains("line1\\n\\\"line2\\\""));
    }

    @Test
    void sendMessageAllowsFollowUpWhenSessionAlreadyRunning() {
        WorkbenchSession session = createUpdateSession();

        service.sendMessage(session.getId(), "user-1", "first message");
        service.sendMessage(session.getId(), "user-1", "second message");

        assertThat(runtime.lastStart.message()).isEqualTo("second message");
        assertThat(events.rows)
                .filteredOn(event -> event.getEventType() == WorkbenchSessionEventType.USER_MESSAGE)
                .extracting(WorkbenchSessionEvent::getPayloadJson)
                .contains(
                        "{\"message\":\"first message\"}",
                        "{\"message\":\"second message\"}");
    }

    @Test
    void findActiveRunDelegatesToRuntimeAfterAuthorization() {
        WorkbenchSession session = createUpdateSession();
        runtime.activeRun = Optional.of(new AgentRuntimeRunHandle(
                "run-active",
                AgentRuntimeRunStatus.STARTED,
                "模型正在回复"));

        Optional<AgentRuntimeRunHandle> activeRun = service.findActiveRun(session.getId(), "user-1");

        assertThat(activeRun).isPresent();
        assertThat(activeRun.get().runId()).isEqualTo("run-active");
        assertThat(runtime.lastActiveRunUserId).isEqualTo("user-1");
        assertThat(runtime.lastActiveRunSessionId).isEqualTo(session.getId());
    }

    @Test
    void findActiveRunReturnsEmptyWhenRuntimePortIsMissing() {
        WorkbenchSession session = createUpdateSession();
        WorkbenchSessionApplicationService serviceWithoutRuntime = newService(null);

        assertThat(serviceWithoutRuntime.findActiveRun(session.getId(), "user-1")).isEmpty();
    }

    @Test
    void sendMessageMarksSessionFailedWhenRuntimeFails() {
        WorkbenchSession session = createUpdateSession();
        runtime.failStart = true;

        assertThatThrownBy(() -> service.sendMessage(session.getId(), "user-1", "create skill"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("runtime unavailable");

        assertThat(sessions.findById(session.getId()).orElseThrow().getStatus())
                .isEqualTo(WorkbenchSessionStatus.FAILED);
        assertThat(events.rows)
                .extracting(WorkbenchSessionEvent::getEventType)
                .contains(WorkbenchSessionEventType.ERROR, WorkbenchSessionEventType.STATUS_CHANGED);
        assertThat(events.rows)
                .extracting(WorkbenchSessionEvent::getPayloadJson)
                .anyMatch(payload -> payload.contains("runtime.failed"));
    }

    @Test
    void cancelRunDelegatesToRuntimeAndMarksSessionCancelled() {
        WorkbenchSession session = createUpdateSession();
        runtime.nextCancel = new AgentRuntimeRunHandle(
                "run-1",
                AgentRuntimeRunStatus.CANCELLED,
                "cancelled",
                List.of(AgentRuntimeEvent.modelMessage("cancelled")));

        AgentRuntimeRunHandle handle = service.cancelRun(session.getId(), "user-1", "run-1");

        assertThat(handle.status()).isEqualTo(AgentRuntimeRunStatus.CANCELLED);
        assertThat(runtime.lastCancel.runId()).isEqualTo("run-1");
        assertThat(sessions.findById(session.getId()).orElseThrow().getStatus())
                .isEqualTo(WorkbenchSessionStatus.CANCELLED);
        assertThat(events.rows)
                .extracting(WorkbenchSessionEvent::getPayloadJson)
                .anyMatch(payload -> payload.contains("runtime.cancelled"));
    }

    @Test
    void rejectedCancelDoesNotFailSession() {
        WorkbenchSession session = createUpdateSession();
        runtime.nextCancel = new AgentRuntimeRunHandle(
                "missing-run",
                AgentRuntimeRunStatus.REJECTED,
                "runtime run is not active",
                List.of(AgentRuntimeEvent.error("missing")));

        AgentRuntimeRunHandle handle = service.cancelRun(session.getId(), "user-1", "missing-run");

        assertThat(handle.status()).isEqualTo(AgentRuntimeRunStatus.REJECTED);
        assertThat(sessions.findById(session.getId()).orElseThrow().getStatus())
                .isEqualTo(WorkbenchSessionStatus.DRAFT);
        assertThat(events.rows)
                .extracting(WorkbenchSessionEvent::getPayloadJson)
                .anyMatch(payload -> payload.contains("runtime.cancel_rejected")
                        && payload.contains("\"runId\":\"missing-run\""))
                .noneMatch(payload -> payload.contains("runtime.cancel_failed"));
    }

    @Test
    void storageSuccessSnapshotFailurePropagatesAndDoesNotAuditSuccessfulFileEdit() {
        WorkbenchSession session = createUpdateSession();
        snapshots.failNextSave = true;

        assertThatThrownBy(() -> service.writeFile(session.getId(), "user-1", "SKILL.md",
                "new".getBytes(StandardCharsets.UTF_8), "text/markdown"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("snapshot save failed");

        assertThat(storage.read(session.getWorkspaceKey(), "SKILL.md")).isPresent();
        assertThat(events.rows)
                .extracting(WorkbenchSessionEvent::getPayloadJson)
                .noneMatch(payload -> payload.contains("file.written"));
    }

    @Test
    void updateMcpBindingsDeduplicatesActiveServersAndDisablesRemovedSelections() {
        WorkbenchSession session = createUpdateSession();
        mcpCatalog.allow("server-a");
        mcpCatalog.allow("server-b");

        List<WorkbenchMcpBinding> first = service.updateMcpBindings(
                session.getId(),
                "user-1",
                new WorkbenchMcpBindingUpdateCommand(
                        List.of("server-a", "server-a", "server-b"),
                        null));
        List<WorkbenchMcpBinding> second = service.updateMcpBindings(
                session.getId(),
                "user-1",
                new WorkbenchMcpBindingUpdateCommand(
                        List.of("server-a"),
                        null));

        assertThat(first).extracting(WorkbenchMcpBinding::getMcpServerId)
                .containsExactly("server-a", "server-b");
        assertThat(second).extracting(WorkbenchMcpBinding::getMcpServerId)
                .containsExactly("server-a");
        assertThat(mcpBindings.findBySessionIdAndStatus(session.getId(), WorkbenchMcpBindingStatus.ENABLED))
                .extracting(WorkbenchMcpBinding::getMcpServerId)
                .containsExactly("server-a");
        assertThat(mcpBindings.rows.values())
                .filteredOn(binding -> binding.getMcpServerId().equals("server-a")
                        && binding.getStatus() == WorkbenchMcpBindingStatus.ENABLED)
                .hasSize(1);

        List<WorkbenchMcpBinding> reenabled = service.updateMcpBindings(
                session.getId(),
                "user-1",
                new WorkbenchMcpBindingUpdateCommand(
                        List.of("server-b"),
                        null));

        assertThat(reenabled).extracting(WorkbenchMcpBinding::getMcpServerId)
                .containsExactly("server-b");
        assertThat(mcpBindings.rows.values())
                .filteredOn(binding -> binding.getSessionId().equals(session.getId())
                        && binding.getMcpServerId().equals("server-b"))
                .hasSize(1)
                .singleElement()
                .extracting(WorkbenchMcpBinding::getStatus)
                .isEqualTo(WorkbenchMcpBindingStatus.ENABLED);
    }

    @Test
    void updateMcpBindingsAllowsRunningSessionsForNextTurnToolPreferences() {
        WorkbenchSession session = createUpdateSession();
        session.start(CLOCK);
        sessions.save(session);
        mcpCatalog.allow("server-a");

        List<WorkbenchMcpBinding> bindings = service.updateMcpBindings(
                session.getId(),
                "user-1",
                new WorkbenchMcpBindingUpdateCommand(List.of("server-a"), null));

        assertThat(bindings).extracting(WorkbenchMcpBinding::getMcpServerId)
                .containsExactly("server-a");
        assertThat(sessions.findById(session.getId()).orElseThrow().getStatus())
                .isEqualTo(WorkbenchSessionStatus.RUNNING);
    }

    @Test
    void updateMcpBindingsRejectsUnknownDisabledOrInvalidServers() {
        WorkbenchSession session = createUpdateSession();
        mcpCatalog.allow("server-a");

        assertThatThrownBy(() -> service.updateMcpBindings(
                session.getId(),
                "user-1",
                new WorkbenchMcpBindingUpdateCommand(List.of("unknown-server"), null)))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("unavailable");

        assertThatThrownBy(() -> service.updateMcpBindings(
                session.getId(),
                "user-1",
                new WorkbenchMcpBindingUpdateCommand(List.of("bad server"), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid");

        assertThat(mcpBindings.findBySessionId(session.getId())).isEmpty();
    }

    @Test
    void approvalRequiredRuntimeEventCreatesRedactedApprovalAndWaitsForApproval() {
        WorkbenchSession session = createUpdateSession();
        runtime.nextStart = new AgentRuntimeRunHandle(
                "run-approval",
                AgentRuntimeRunStatus.STARTED,
                "approval required",
                List.of(new AgentRuntimeEvent(
                        AgentRuntimeEventType.APPROVAL_REQUIRED,
                        """
                                {"toolName":"deploy","mcpServerId":"server-a","arguments":{"target":"prod","apiToken":"secret"}}
                                """,
                        null,
                        null,
                        null)));

        service.sendMessage(session.getId(), "user-1", "deploy it");

        assertThat(sessions.findById(session.getId()).orElseThrow().getStatus())
                .isEqualTo(WorkbenchSessionStatus.WAITING_APPROVAL);
        assertThat(approvals.findPendingBySessionId(session.getId()))
                .singleElement()
                .satisfies(approval -> {
                    assertThat(approval.getToolName()).isEqualTo("deploy");
                    assertThat(approval.getMcpServerId()).isEqualTo("server-a");
                    assertThat(approval.getRiskLevel()).isEqualTo(WorkbenchToolRiskLevel.UNKNOWN);
                    assertThat(approval.getArgumentsRedactedJson()).contains("\"target\":\"[REDACTED]\"");
                    assertThat(approval.getArgumentsRedactedJson()).contains("\"apiToken\":\"[REDACTED]\"");
                    assertThat(approval.getArgumentsRedactedJson()).doesNotContain("prod");
                    assertThat(approval.getArgumentsRedactedJson()).doesNotContain("secret");
                });
        assertThat(events.rows)
                .filteredOn(event -> event.getEventType() == WorkbenchSessionEventType.APPROVAL_REQUIRED)
                .extracting(WorkbenchSessionEvent::getPayloadJson)
                .singleElement()
                .satisfies(payload -> {
                    assertThat(payload).contains("\"runId\":\"run-approval\"");
                    assertThat(payload).contains("\"arguments\":\"[REDACTED]\"");
                    assertThat(payload).doesNotContain("prod");
                    assertThat(payload).doesNotContain("secret");
                    assertThat(payload).doesNotContain("apiToken");
                });
    }

    @Test
    void toolCallRuntimeEventIsRedactedBeforePersistence() {
        WorkbenchSession session = createUpdateSession();
        runtime.nextStart = new AgentRuntimeRunHandle(
                "run-tool",
                AgentRuntimeRunStatus.STARTED,
                "tool called",
                List.of(new AgentRuntimeEvent(
                        AgentRuntimeEventType.TOOL_CALL,
                        """
                                {"toolName":"search","arguments":{"query":"secret"},"streamableHttpUrl":"http://internal.example/mcp","runtimeEndpointRef":"secret-ref"}
                                """,
                        null,
                        null,
                        null)));

        service.sendMessage(session.getId(), "user-1", "call search");

        assertThat(events.rows)
                .filteredOn(event -> event.getEventType() == WorkbenchSessionEventType.TOOL_CALL)
                .extracting(WorkbenchSessionEvent::getPayloadJson)
                .singleElement()
                .satisfies(payload -> {
                    assertThat(payload).contains("\"runId\":\"run-tool\"");
                    assertThat(payload).contains("\"arguments\":\"[REDACTED]\"");
                    assertThat(payload).contains("\"streamableHttpUrl\":\"[REDACTED]\"");
                    assertThat(payload).contains("\"runtimeEndpointRef\":\"[REDACTED]\"");
                    assertThat(payload).doesNotContain("secret");
                    assertThat(payload).doesNotContain("internal.example");
                });
    }

    @Test
    void nestedRuntimeEventArraysAreRedactedBeforePersistence() {
        WorkbenchSession session = createUpdateSession();
        runtime.nextStart = new AgentRuntimeRunHandle(
                "run-tool-array",
                AgentRuntimeRunStatus.STARTED,
                "tool called",
                List.of(new AgentRuntimeEvent(
                        AgentRuntimeEventType.TOOL_CALL,
                        """
                                {"toolCalls":[{"name":"deploy","arguments":{"target":"prod","token":"secret"},"endpointUrl":"https://internal.example/mcp"}]}
                                """,
                        null,
                        null,
                        null)));

        service.sendMessage(session.getId(), "user-1", "call deploy");

        assertThat(events.rows)
                .filteredOn(event -> event.getEventType() == WorkbenchSessionEventType.TOOL_CALL)
                .extracting(WorkbenchSessionEvent::getPayloadJson)
                .singleElement()
                .satisfies(payload -> {
                    assertThat(payload).contains("\"arguments\":\"[REDACTED]\"");
                    assertThat(payload).contains("\"endpointUrl\":\"[REDACTED]\"");
                    assertThat(payload).doesNotContain("prod");
                    assertThat(payload).doesNotContain("secret");
                    assertThat(payload).doesNotContain("internal.example");
                });
    }

    @Test
    void runtimeFailureMessagesAreRedactedBeforePersistence() {
        WorkbenchSession session = createUpdateSession();
        runtime.nextStart = new AgentRuntimeRunHandle(
                "run-failed",
                AgentRuntimeRunStatus.FAILED,
                "token=secret https://internal.example/runtime",
                List.of());

        service.sendMessage(session.getId(), "user-1", "fail runtime");

        assertThat(events.rows)
                .filteredOn(event -> event.getEventType() == WorkbenchSessionEventType.ERROR)
                .extracting(WorkbenchSessionEvent::getPayloadJson)
                .singleElement()
                .satisfies(payload -> {
                    assertThat(payload).contains("\"runId\":\"run-failed\"");
                    assertThat(payload).contains("token=[REDACTED]");
                    assertThat(payload).contains("[REDACTED_URL]");
                    assertThat(payload).doesNotContain("secret");
                    assertThat(payload).doesNotContain("internal.example");
                });
    }

    @Test
    void runtimeErrorEventPayloadTextLeavesAreRedactedBeforePersistence() {
        WorkbenchSession session = createUpdateSession();
        runtime.nextStart = new AgentRuntimeRunHandle(
                "run-error-event",
                AgentRuntimeRunStatus.STARTED,
                "runtime started",
                List.of(new AgentRuntimeEvent(
                        AgentRuntimeEventType.ERROR,
                        """
                                {
                                  "message":"token=secret https://internal.example/runtime",
                                  "details":{"cause":"api_key=prod-secret Authorization: Bearer header-secret"},
                                  "errors":[{"detail":"authorization=Bearer nested-secret http://internal.example/mcp"}]
                                }
                                """,
                        null,
                        null,
                        null)));

        service.sendMessage(session.getId(), "user-1", "trigger error event");

        assertThat(events.rows)
                .filteredOn(event -> event.getEventType() == WorkbenchSessionEventType.ERROR)
                .extracting(WorkbenchSessionEvent::getPayloadJson)
                .singleElement()
                .satisfies(payload -> {
                    assertThat(payload).contains("\"runId\":\"run-error-event\"");
                    assertThat(payload).contains("token=[REDACTED]");
                    assertThat(payload).contains("api_key=[REDACTED]");
                    assertThat(payload).contains("authorization=[REDACTED]");
                    assertThat(payload).contains("[REDACTED_URL]");
                    assertThat(payload).doesNotContain("secret");
                    assertThat(payload).doesNotContain("prod-secret");
                    assertThat(payload).doesNotContain("header-secret");
                    assertThat(payload).doesNotContain("nested-secret");
                    assertThat(payload).doesNotContain("Bearer");
                    assertThat(payload).doesNotContain("internal.example");
                });
    }

    @Test
    void approveAndRejectCanOnlyDecidePendingApprovalOnceAndApproveResumesSessionStatus() {
        WorkbenchSession approveSession = sessionWaitingForApproval("deploy");
        WorkbenchToolApproval approval = approvals.findPendingBySessionId(approveSession.getId()).getFirst();

        WorkbenchToolApproval approved = service.approveToolApproval(
                approveSession.getId(),
                approval.getId(),
                "user-1");

        assertThat(approved.getStatus()).isEqualTo(WorkbenchToolApprovalStatus.APPROVED);
        assertThat(sessions.findById(approveSession.getId()).orElseThrow().getStatus())
                .isEqualTo(WorkbenchSessionStatus.RUNNING);
        assertThatThrownBy(() -> service.rejectToolApproval(approveSession.getId(), approval.getId(), "user-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already decided");

        WorkbenchSession rejectSession = sessionWaitingForApproval("delete");
        WorkbenchToolApproval rejection = approvals.findPendingBySessionId(rejectSession.getId()).getFirst();

        WorkbenchToolApproval rejected = service.rejectToolApproval(
                rejectSession.getId(),
                rejection.getId(),
                "user-1");

        assertThat(rejected.getStatus()).isEqualTo(WorkbenchToolApprovalStatus.REJECTED);
        assertThat(sessions.findById(rejectSession.getId()).orElseThrow().getStatus())
                .isEqualTo(WorkbenchSessionStatus.FAILED);
        assertThat(events.rows)
                .extracting(WorkbenchSessionEvent::getEventType)
                .contains(WorkbenchSessionEventType.APPROVAL_DECIDED);
        assertThat(events.rows)
                .filteredOn(event -> event.getSessionId().equals(rejectSession.getId()))
                .filteredOn(event -> event.getEventType() == WorkbenchSessionEventType.ERROR)
                .extracting(WorkbenchSessionEvent::getPayloadJson)
                .singleElement()
                .satisfies(payload -> {
                    assertThat(payload).contains("runtime.approval_rejected");
                    assertThat(payload).contains("\"runId\":\"run-delete\"");
                });
    }

    @Test
    void pendingApprovalBlocksReadyForReviewAndDirectMutations() {
        WorkbenchSession session = sessionWaitingForApproval("deploy");
        mcpCatalog.allow("server-a");

        assertThatThrownBy(() -> service.markReadyForReview(session.getId(), "user-1"))
                .isInstanceOf(DomainBadRequestException.class)
                .hasMessage("error.workbench.session.pendingApprovals");
        assertThatThrownBy(() -> service.updateMcpBindings(
                session.getId(),
                "user-1",
                new WorkbenchMcpBindingUpdateCommand(List.of("server-a"), null)))
                .isInstanceOf(DomainBadRequestException.class)
                .hasMessage("error.workbench.session.notEditable");
        assertThatThrownBy(() -> service.writeFile(
                session.getId(),
                "user-1",
                "SKILL.md",
                "updated".getBytes(StandardCharsets.UTF_8),
                "text/markdown"))
                .isInstanceOf(DomainBadRequestException.class)
                .hasMessage("error.workbench.session.notEditable");
        assertThatThrownBy(() -> service.deleteFile(session.getId(), "user-1", "SKILL.md"))
                .isInstanceOf(DomainBadRequestException.class)
                .hasMessage("error.workbench.session.notEditable");
        assertThatThrownBy(() -> service.importSourceVersion(session.getId(), "user-1"))
                .isInstanceOf(DomainBadRequestException.class)
                .hasMessage("error.workbench.session.notEditable");

        assertThat(sessions.findById(session.getId()).orElseThrow().getStatus())
                .isEqualTo(WorkbenchSessionStatus.WAITING_APPROVAL);
    }

    @Test
    void packagePreviewExcludesRuntimeArtifactsBeforePublishValidationAndPersistsCandidate() {
        WorkbenchSession session = createUpdateSession();
        putCurrentSnapshot(session, "SKILL.md", skillMd("existing-skill", "1.0.1"));
        putCurrentSnapshot(session, "references/guide.md", "guide");
        putCurrentSnapshot(session, "AGENTS.md", "runtime prompt");
        putCurrentSnapshot(session, ".workbench/session.json", "{}");
        putCurrentSnapshot(session, ".agentscope/run.json", "{}");
        putCurrentSnapshot(session, "runtime/session.log", "internal");

        WorkbenchPackagePreviewResult preview = service.previewPackage(
                session.getId(), "user-1", SkillVisibility.PRIVATE, Set.of("USER"));

        assertThat(preview.readyToPublish()).isTrue();
        assertThat(preview.packageFingerprint()).startsWith("sha256:");
        assertThat(preview.includedFiles())
                .extracting(WorkbenchPackageFile::path)
                .containsExactly("SKILL.md", "references/guide.md");
        assertThat(preview.excludedFiles())
                .extracting(WorkbenchPackageExcludedFile::path)
                .containsExactlyInAnyOrder("AGENTS.md", ".agentscope/run.json", ".workbench/session.json", "runtime/session.log");
        assertThat(publisher.validatedEntries)
                .extracting(PackageEntry::path)
                .containsExactly("SKILL.md", "references/guide.md");
        assertThat(candidates.findLatestBySessionId(session.getId())).get()
                .extracting(WorkbenchPublishCandidate::getPackageFingerprint,
                        WorkbenchPublishCandidate::getValidationStatus,
                        WorkbenchPublishCandidate::getFileCount)
                .containsExactly(preview.packageFingerprint(), WorkbenchPublishValidationStatus.PASSED, 2);
    }

    @Test
    void packagePreviewWithoutSkillMdIsInvalidAndDoesNotCallPublish() {
        WorkbenchSession session = createUpdateSession();
        putCurrentSnapshot(session, "references/guide.md", "guide");

        WorkbenchPackagePreviewResult preview = service.previewPackage(
                session.getId(), "user-1", SkillVisibility.PRIVATE, Set.of("USER"));

        assertThat(preview.readyToPublish()).isFalse();
        assertThat(preview.validation().status()).isEqualTo("FAIL");
        assertThat(preview.validation().messages()).contains("Missing required file: SKILL.md at root");
        assertThat(candidates.findLatestBySessionId(session.getId())).get()
                .extracting(WorkbenchPublishCandidate::getValidationStatus)
                .isEqualTo(WorkbenchPublishValidationStatus.FAILED);
        assertThat(publisher.publishedEntries).isEmpty();
    }

    @Test
    void packagePreviewRejectsResolvedSlugThatDoesNotMatchSessionTarget() {
        WorkbenchSession session = createUpdateSession();
        putCurrentSnapshot(session, "SKILL.md", skillMd("other-skill", "1.0.1"));
        publisher.nextResolvedSlug = "other-skill";

        WorkbenchPackagePreviewResult preview = service.previewPackage(
                session.getId(), "user-1", SkillVisibility.PRIVATE, Set.of("USER"));

        assertThat(preview.readyToPublish()).isFalse();
        assertThat(preview.validation().status()).isEqualTo("FAIL");
        assertThat(preview.validation().messages())
                .anyMatch(message -> message.contains("Resolved skill slug does not match"));
        assertThat(candidates.findLatestBySessionId(session.getId())).get()
                .extracting(WorkbenchPublishCandidate::getValidationStatus)
                .isEqualTo(WorkbenchPublishValidationStatus.FAILED);
        assertThat(publisher.publishedEntries).isEmpty();
    }

    @Test
    void publishRejectsFingerprintMismatchBeforeCallingSkillPublishFlow() {
        WorkbenchSession session = createUpdateSession();
        putCurrentSnapshot(session, "SKILL.md", skillMd("existing-skill", "1.0.1"));
        WorkbenchPackagePreviewResult preview = service.previewPackage(
                session.getId(), "user-1", SkillVisibility.PRIVATE, Set.of("USER"));
        service.markReadyForReview(session.getId(), "user-1");

        assertThatThrownBy(() -> service.publishPackage(
                session.getId(),
                "user-1",
                new WorkbenchPackagePublishCommand("sha256:mismatch", SkillVisibility.PRIVATE, Set.of("USER"))))
                .isInstanceOf(DomainBadRequestException.class)
                .hasMessage("error.workbench.publish.fingerprint.previewMismatch");

        assertThat(preview.readyToPublish()).isTrue();
        assertThat(publisher.publishedEntries).isEmpty();
        assertThat(sessions.findById(session.getId()).orElseThrow().getStatus())
                .isEqualTo(WorkbenchSessionStatus.READY_FOR_REVIEW);
    }

    @Test
    void duplicatePublishedVersionConflictDoesNotMarkCandidateOrSessionPublished() {
        WorkbenchSession session = createUpdateSession();
        putCurrentSnapshot(session, "SKILL.md", skillMd("existing-skill", "1.0.1"));
        WorkbenchPackagePreviewResult preview = service.previewPackage(
                session.getId(), "user-1", SkillVisibility.PUBLIC, Set.of("USER"));
        service.markReadyForReview(session.getId(), "user-1");
        publisher.publishFailure = new DomainBadRequestException("error.skill.version.exists", "1.0.1");

        assertThatThrownBy(() -> service.publishPackage(
                session.getId(),
                "user-1",
                new WorkbenchPackagePublishCommand(preview.packageFingerprint(), SkillVisibility.PUBLIC, Set.of("USER"))))
                .isInstanceOf(DomainBadRequestException.class)
                .hasMessage("error.skill.version.exists");

        assertThat(candidates.findLatestBySessionId(session.getId())).get()
                .extracting(WorkbenchPublishCandidate::getValidationStatus, WorkbenchPublishCandidate::getSkillVersionId)
                .containsExactly(WorkbenchPublishValidationStatus.PASSED, null);
        assertThat(sessions.findById(session.getId()).orElseThrow().getStatus())
                .isEqualTo(WorkbenchSessionStatus.READY_FOR_REVIEW);
    }

    @Test
    void publishCanConfirmPreviewDirectlyFromDraftSession() {
        WorkbenchSession session = createUpdateSession();
        putCurrentSnapshot(session, "SKILL.md", skillMd("existing-skill", "1.0.1"));
        WorkbenchPackagePreviewResult preview = service.previewPackage(
                session.getId(), "user-1", SkillVisibility.PRIVATE, Set.of("USER"));

        WorkbenchPackagePublishResult result = service.publishPackage(
                session.getId(),
                "user-1",
                new WorkbenchPackagePublishCommand(preview.packageFingerprint(), SkillVisibility.PRIVATE, Set.of("USER")));

        assertThat(result.skillVersionId()).isEqualTo(900L);
        assertThat(sessions.findById(session.getId()).orElseThrow().getStatus())
                .isEqualTo(WorkbenchSessionStatus.PUBLISHED);
        assertThat(events.rows)
                .extracting(WorkbenchSessionEvent::getPayloadJson)
                .anyMatch(payload -> payload.contains("package.ready_for_review"))
                .anyMatch(payload -> payload.contains("package.published"));
    }

    @Test
    void updateSessionPublishesFromCurrentSnapshotWithoutMutatingSourceVersionFiles() {
        source.put(200L, List.of(new SourceFixture("SKILL.md", skillMd("existing-skill", "1.0.0"), "text/markdown")));
        WorkbenchSession session = createUpdateSession();
        service.importSourceVersion(session.getId(), "user-1");
        WorkbenchPackagePreviewResult preview = service.previewPackage(
                session.getId(), "user-1", SkillVisibility.PRIVATE, Set.of("SUPER_ADMIN"));
        service.markReadyForReview(session.getId(), "user-1");

        WorkbenchPackagePublishResult result = service.publishPackage(
                session.getId(),
                "user-1",
                new WorkbenchPackagePublishCommand(preview.packageFingerprint(), SkillVisibility.PRIVATE, Set.of("SUPER_ADMIN")));

        assertThat(result.skillVersionId()).isEqualTo(900L);
        assertThat(publisher.publishedEntries).singleElement()
                .satisfies(entry -> assertThat(new String(entry.content(), StandardCharsets.UTF_8))
                        .contains("version: 1.0.1"));
        assertThat(source.readFixture(200L, "SKILL.md")).contains("version: 1.0.0");
        assertThat(sessions.findById(session.getId()).orElseThrow().getStatus())
                .isEqualTo(WorkbenchSessionStatus.PUBLISHED);
        assertThat(candidates.findLatestBySessionId(session.getId())).get()
                .extracting(WorkbenchPublishCandidate::getValidationStatus, WorkbenchPublishCandidate::getSkillVersionId)
                .containsExactly(WorkbenchPublishValidationStatus.PUBLISHED, 900L);
    }

    @Test
    void readyForReviewSessionRejectsDirectMutations() {
        WorkbenchSession session = createUpdateSession();
        service.markReadyForReview(session.getId(), "user-1");

        assertThatThrownBy(() -> service.writeFile(
                session.getId(),
                "user-1",
                "SKILL.md",
                "updated".getBytes(StandardCharsets.UTF_8),
                "text/markdown"))
                .isInstanceOf(DomainBadRequestException.class)
                .hasMessage("error.workbench.session.notEditable");
        assertThatThrownBy(() -> service.updateMcpBindings(
                session.getId(),
                "user-1",
                new WorkbenchMcpBindingUpdateCommand(List.of(), null)))
                .isInstanceOf(DomainBadRequestException.class)
                .hasMessage("error.workbench.session.notEditable");
    }

    @Test
    void mcpBindingAndApprovalApisRejectNonOwner() {
        WorkbenchSession session = createUpdateSession();

        assertThatThrownBy(() -> service.listMcpBindings(session.getId(), "other-user"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("access");
        assertThatThrownBy(() -> service.listPendingApprovals(session.getId(), "other-user"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("access");
    }

    private WorkbenchSession createUpdateSession() {
        return service.createSession(new CreateWorkbenchSessionCommand(
                "user-1", 10L, WorkbenchMode.UPDATE_SKILL, 100L, 200L,
                "existing-skill", "1.0.1", Instant.parse("2026-08-06T00:00:00Z")));
    }

    private WorkbenchSession sessionWaitingForApproval(String toolName) {
        WorkbenchSession session = createUpdateSession();
        runtime.nextStart = new AgentRuntimeRunHandle(
                "run-" + toolName,
                AgentRuntimeRunStatus.STARTED,
                "approval required",
                List.of(new AgentRuntimeEvent(
                        AgentRuntimeEventType.APPROVAL_REQUIRED,
                        "{\"toolName\":\"" + toolName + "\",\"risk\":\"MUTATING\",\"arguments\":{}}",
                        null,
                        null,
                        null)));
        service.sendMessage(session.getId(), "user-1", "call " + toolName);
        return session;
    }

    private void putCurrentSnapshot(WorkbenchSession session, String path, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        storage.write(session.getWorkspaceKey(), path, bytes, "text/markdown");
        snapshots.save(new WorkbenchFileSnapshot(
                session.getId(),
                WorkbenchFileSnapshotType.CURRENT,
                path,
                path,
                sha256(bytes),
                (long) bytes.length,
                "text/markdown",
                session.getExpiresAt(),
                CLOCK));
    }

    private String skillMd(String name, String version) {
        return "---\n"
                + "name: " + name + "\n"
                + "description: Preview skill\n"
                + "version: " + version + "\n"
                + "---\n"
                + "Use this skill.\n";
    }

    private record SourceFixture(String path, String content, String contentType) {
    }

    private static final class FakeAuthorization implements WorkbenchAuthorizationPort {
        private boolean denyCreate;
        private boolean denyUpdateSource;

        @Override
        public void assertCanCreateSession(String userId, Long namespaceId) {
            if (denyCreate) {
                throw new SecurityException("user cannot create workbench sessions in namespace " + namespaceId);
            }
        }

        @Override
        public void assertCanCreateSession(CreateWorkbenchSessionCommand command) {
            assertCanCreateSession(command.userId(), command.namespaceId());
            if (denyUpdateSource && command.mode() == WorkbenchMode.UPDATE_SKILL) {
                throw new SecurityException("user cannot start update session for source version "
                        + command.sourceVersionId());
            }
        }

        @Override
        public void assertCanAccessSession(String userId, WorkbenchSession session) {
            if (!session.getUserId().equals(userId)) {
                throw new SecurityException("user cannot access workbench session");
            }
        }

        @Override
        public void assertCanImportSourceVersion(String userId, WorkbenchSession session) {
            assertCanAccessSession(userId, session);
        }
    }

    private static final class InMemorySessionRepository implements WorkbenchSessionRepository {
        private final Map<Long, WorkbenchSession> rows = new LinkedHashMap<>();
        private long sequence = 1L;

        @Override
        public Optional<WorkbenchSession> findById(Long id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public Optional<WorkbenchSession> findByIdAndUserId(Long id, String userId) {
            return findById(id).filter(session -> session.getUserId().equals(userId));
        }

        @Override
        public List<WorkbenchSession> findByUserId(String userId, int limit) {
            return rows.values().stream()
                    .filter(session -> session.getUserId().equals(userId))
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<WorkbenchSession> findByUserIdAndStatus(String userId, WorkbenchSessionStatus status) {
            return rows.values().stream()
                    .filter(session -> session.getUserId().equals(userId) && session.getStatus() == status)
                    .toList();
        }

        @Override
        public List<WorkbenchSession> findExpiredSessions(Instant now, int limit) {
            return rows.values().stream()
                    .filter(session -> session.getExpiresAt().isBefore(now))
                    .limit(limit)
                    .toList();
        }

        @Override
        public WorkbenchSession save(WorkbenchSession session) {
            if (session.getId() == null) {
                setField(session, "id", sequence++);
            }
            rows.put(session.getId(), session);
            return session;
        }
    }

    private static final class InMemoryEventRepository implements WorkbenchSessionEventRepository {
        private final List<WorkbenchSessionEvent> rows = new ArrayList<>();
        private long sequence = 1L;

        @Override
        public WorkbenchSessionEvent append(WorkbenchSessionEvent event) {
            setField(event, "id", sequence++);
            rows.add(event);
            return event;
        }

        @Override
        public Optional<WorkbenchSessionEvent> findBySessionIdAndEventId(Long sessionId, Long eventId) {
            return rows.stream()
                    .filter(event -> event.getSessionId().equals(sessionId) && event.getId().equals(eventId))
                    .findFirst();
        }

        @Override
        public List<WorkbenchSessionEvent> findBySessionId(Long sessionId, int limit) {
            List<WorkbenchSessionEvent> matches = rows.stream()
                    .filter(event -> event.getSessionId().equals(sessionId))
                    .toList();
            return matches.stream()
                    .skip(Math.max(0, matches.size() - limit))
                    .toList();
        }

        @Override
        public List<WorkbenchSessionEvent> findBySessionIdAfterEventId(Long sessionId, Long afterEventId, int limit) {
            return rows.stream()
                    .filter(event -> event.getSessionId().equals(sessionId) && event.getId() > afterEventId)
                    .limit(limit)
                    .toList();
        }
    }

    private static final class InMemoryFileSnapshotRepository implements WorkbenchFileSnapshotRepository {
        private final Map<String, WorkbenchFileSnapshot> rows = new LinkedHashMap<>();
        private long sequence = 1L;
        private boolean failNextSave;

        @Override
        public WorkbenchFileSnapshot save(WorkbenchFileSnapshot snapshot) {
            if (failNextSave) {
                failNextSave = false;
                throw new IllegalStateException("snapshot save failed");
            }
            setField(snapshot, "id", sequence++);
            rows.put(key(snapshot.getSessionId(), snapshot.getSnapshotType(), snapshot.getFilePath()), snapshot);
            return snapshot;
        }

        @Override
        public Optional<WorkbenchFileSnapshot> findBySessionIdAndTypeAndFilePath(
                Long sessionId, WorkbenchFileSnapshotType snapshotType, String filePath) {
            return Optional.ofNullable(rows.get(key(sessionId, snapshotType, filePath)));
        }

        @Override
        public List<WorkbenchFileSnapshot> findBySessionIdAndType(Long sessionId, WorkbenchFileSnapshotType snapshotType) {
            return rows.values().stream()
                    .filter(snapshot -> snapshot.getSessionId().equals(sessionId)
                            && snapshot.getSnapshotType() == snapshotType)
                    .sorted(Comparator.comparing(WorkbenchFileSnapshot::getFilePath))
                    .toList();
        }

        @Override
        public void deleteBySessionIdAndTypeAndFilePath(Long sessionId, WorkbenchFileSnapshotType snapshotType, String filePath) {
            rows.remove(key(sessionId, snapshotType, filePath));
        }

        private String key(Long sessionId, WorkbenchFileSnapshotType snapshotType, String filePath) {
            return sessionId + ":" + snapshotType + ":" + filePath;
        }
    }

    private static final class InMemoryMcpBindingRepository implements WorkbenchMcpBindingRepository {
        private final Map<Long, WorkbenchMcpBinding> rows = new LinkedHashMap<>();
        private long sequence = 1L;

        @Override
        public WorkbenchMcpBinding save(WorkbenchMcpBinding binding) {
            if (binding.getId() == null) {
                setField(binding, "id", sequence++);
            }
            rows.put(binding.getId(), binding);
            return binding;
        }

        @Override
        public Optional<WorkbenchMcpBinding> findByIdAndSessionId(Long id, Long sessionId) {
            return Optional.ofNullable(rows.get(id))
                    .filter(binding -> binding.getSessionId().equals(sessionId));
        }

        @Override
        public List<WorkbenchMcpBinding> findBySessionId(Long sessionId) {
            return rows.values().stream()
                    .filter(binding -> binding.getSessionId().equals(sessionId))
                    .sorted(Comparator.comparing(WorkbenchMcpBinding::getId))
                    .toList();
        }

        @Override
        public List<WorkbenchMcpBinding> findBySessionIdAndStatus(Long sessionId, WorkbenchMcpBindingStatus status) {
            return rows.values().stream()
                    .filter(binding -> binding.getSessionId().equals(sessionId) && binding.getStatus() == status)
                    .sorted(Comparator.comparing(WorkbenchMcpBinding::getId))
                    .toList();
        }
    }

    private static final class FakeMcpCatalog implements WorkbenchMcpCatalogPort {
        private final Map<String, WorkbenchMcpRuntimeCandidate> candidates = new LinkedHashMap<>();

        void allow(String serverId) {
            candidates.put(serverId, new WorkbenchMcpRuntimeCandidate(
                    serverId,
                    WorkbenchMcpCatalogSource.CONTEXT_FORGE,
                    "context-forge:" + serverId,
                    "https://context-forge.example/servers/" + serverId + "/mcp",
                    "https://context-forge.example/servers/" + serverId + "/sse"));
        }

        @Override
        public List<WorkbenchMcpRuntimeCandidate> resolveRuntimeCandidates(List<String> serverIds) {
            return serverIds.stream()
                    .map(candidates::get)
                    .filter(Objects::nonNull)
                    .toList();
        }
    }

    private static final class InMemoryToolApprovalRepository implements WorkbenchToolApprovalRepository {
        private final Map<Long, WorkbenchToolApproval> rows = new LinkedHashMap<>();
        private long sequence = 1L;

        @Override
        public WorkbenchToolApproval save(WorkbenchToolApproval approval) {
            if (approval.getId() == null) {
                setField(approval, "id", sequence++);
            }
            rows.put(approval.getId(), approval);
            return approval;
        }

        @Override
        public Optional<WorkbenchToolApproval> findByIdAndSessionId(Long id, Long sessionId) {
            return Optional.ofNullable(rows.get(id))
                    .filter(approval -> approval.getSessionId().equals(sessionId));
        }

        @Override
        public Optional<WorkbenchToolApproval> findByEventId(Long eventId) {
            return rows.values().stream()
                    .filter(approval -> approval.getEventId().equals(eventId))
                    .findFirst();
        }

        @Override
        public List<WorkbenchToolApproval> findPendingBySessionId(Long sessionId) {
            return rows.values().stream()
                    .filter(approval -> approval.getSessionId().equals(sessionId)
                            && approval.getStatus() == WorkbenchToolApprovalStatus.PENDING)
                    .sorted(Comparator.comparing(WorkbenchToolApproval::getId))
                    .toList();
        }
    }

    private static final class InMemoryPublishCandidateRepository implements WorkbenchPublishCandidateRepository {
        private final Map<Long, WorkbenchPublishCandidate> rows = new LinkedHashMap<>();
        private long sequence = 1L;

        @Override
        public WorkbenchPublishCandidate save(WorkbenchPublishCandidate candidate) {
            if (candidate.getId() == null) {
                setField(candidate, "id", sequence++);
            }
            rows.put(candidate.getId(), candidate);
            return candidate;
        }

        @Override
        public Optional<WorkbenchPublishCandidate> findByIdAndSessionId(Long id, Long sessionId) {
            return Optional.ofNullable(rows.get(id))
                    .filter(candidate -> candidate.getSessionId().equals(sessionId));
        }

        @Override
        public Optional<WorkbenchPublishCandidate> findLatestBySessionId(Long sessionId) {
            return rows.values().stream()
                    .filter(candidate -> candidate.getSessionId().equals(sessionId))
                    .max(Comparator.comparing(WorkbenchPublishCandidate::getCreatedAt));
        }

        @Override
        public List<WorkbenchPublishCandidate> findBySessionId(Long sessionId) {
            return rows.values().stream()
                    .filter(candidate -> candidate.getSessionId().equals(sessionId))
                    .sorted(Comparator.comparing(WorkbenchPublishCandidate::getCreatedAt).reversed())
                    .toList();
        }
    }

    private static final class FakeSkillPublisher implements WorkbenchSkillPublishPort {
        private List<PackageEntry> validatedEntries = List.of();
        private List<PackageEntry> publishedEntries = List.of();
        private RuntimeException publishFailure;
        private String nextResolvedSlug = "existing-skill";
        private String nextResolvedVersion = "1.0.1";

        @Override
        public WorkbenchPublishValidationResult validateOnly(Long namespaceId, List<PackageEntry> entries,
                                                             String publisherId, SkillVisibility visibility,
                                                             Set<String> platformRoles) {
            validatedEntries = List.copyOf(entries);
            boolean hasSkillMd = entries.stream().anyMatch(entry -> entry.path().equals("SKILL.md"));
            if (!hasSkillMd) {
                return new WorkbenchPublishValidationResult(
                        false,
                        List.of("Missing required file: SKILL.md at root"),
                        List.of(),
                        null,
                        null);
            }
            return new WorkbenchPublishValidationResult(true, List.of(), List.of(), nextResolvedSlug, nextResolvedVersion);
        }

        @Override
        public WorkbenchPackagePublishResult publish(Long namespaceId, List<PackageEntry> entries,
                                                     String publisherId, SkillVisibility visibility,
                                                     Set<String> platformRoles) {
            if (publishFailure != null) {
                throw publishFailure;
            }
            publishedEntries = List.copyOf(entries);
            return new WorkbenchPackagePublishResult(
                    800L,
                    900L,
                    "team-a",
                    "existing-skill",
                    "1.0.1",
                    "PUBLISHED");
        }
    }

    private static final class InMemorySkillSource implements WorkbenchSkillSourcePort {
        private final Map<Long, List<SourceFixture>> files = new HashMap<>();

        private void put(Long versionId, List<SourceFixture> fixtures) {
            files.put(versionId, fixtures);
        }

        private String readFixture(Long versionId, String path) {
            return files.getOrDefault(versionId, List.of()).stream()
                    .filter(fixture -> fixture.path().equals(path))
                    .findFirst()
                    .orElseThrow()
                    .content();
        }

        @Override
        public List<WorkbenchSourceFile> listFiles(Long sourceVersionId) {
            return files.getOrDefault(sourceVersionId, List.of()).stream()
                    .map(file -> new WorkbenchSourceFile(
                            file.path(),
                            file.content().getBytes(StandardCharsets.UTF_8).length,
                            file.contentType(),
                            "source/" + sourceVersionId + "/" + file.path()))
                    .toList();
        }

        @Override
        public byte[] readFile(WorkbenchSourceFile file) {
            return files.values().stream()
                    .flatMap(List::stream)
                    .filter(fixture -> fixture.path().equals(file.path()))
                    .findFirst()
                    .orElseThrow()
                    .content()
                    .getBytes(StandardCharsets.UTF_8);
        }
    }

    private static final class InMemoryWorkspaceStorage implements WorkbenchWorkspaceStoragePort {
        private final Map<String, byte[]> objects = new LinkedHashMap<>();

        @Override
        public void write(String workspaceKey, String relativePath, byte[] content, String contentType) {
            objects.put(key(workspaceKey, relativePath), content.clone());
        }

        @Override
        public Optional<byte[]> read(String workspaceKey, String relativePath) {
            byte[] content = objects.get(key(workspaceKey, relativePath));
            return content == null ? Optional.empty() : Optional.of(content.clone());
        }

        @Override
        public List<WorkbenchWorkspaceFile> list(String workspaceKey) {
            String prefix = workspaceKey + "/";
            return objects.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith(prefix))
                    .map(entry -> new WorkbenchWorkspaceFile(
                            entry.getKey().substring(prefix.length()), entry.getValue().length, null))
                    .sorted(Comparator.comparing(WorkbenchWorkspaceFile::path))
                    .toList();
        }

        @Override
        public void delete(String workspaceKey, String relativePath) {
            objects.remove(key(workspaceKey, relativePath));
        }

        private String key(String workspaceKey, String relativePath) {
            return workspaceKey + "/" + relativePath;
        }
    }

    private static final class FakeRuntime implements AgentRuntimePort {
        private AgentRuntimeRunCommand lastStart;
        private AgentRuntimeCancelCommand lastCancel;
        private String lastActiveRunUserId;
        private Long lastActiveRunSessionId;
        private Optional<AgentRuntimeRunHandle> activeRun = Optional.empty();
        private AgentRuntimeRunHandle nextStart = new AgentRuntimeRunHandle(
                "run-default",
                AgentRuntimeRunStatus.STARTED,
                "started");
        private AgentRuntimeRunHandle nextCancel = new AgentRuntimeRunHandle(
                "run-default",
                AgentRuntimeRunStatus.CANCELLED,
                "cancelled");
        private boolean failStart;

        @Override
        public AgentRuntimeRunHandle start(AgentRuntimeRunCommand command) {
            lastStart = command;
            if (failStart) {
                throw new IllegalStateException("runtime unavailable");
            }
            return nextStart;
        }

        @Override
        public AgentRuntimeRunHandle cancel(AgentRuntimeCancelCommand command) {
            lastCancel = command;
            return nextCancel;
        }

        @Override
        public Optional<AgentRuntimeRunHandle> findActiveRun(String userId, Long sessionId) {
            lastActiveRunUserId = userId;
            lastActiveRunSessionId = sessionId;
            return activeRun;
        }
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String sha256(byte[] content) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(content);
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
