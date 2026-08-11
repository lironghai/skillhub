package com.iflytek.skillhub.workbench.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.skill.validation.SkillPackagePolicy;
import com.iflytek.skillhub.workbench.port.AgentRuntimeCancelCommand;
import com.iflytek.skillhub.workbench.port.AgentRuntimeEvent;
import com.iflytek.skillhub.workbench.port.AgentRuntimeMcpServer;
import com.iflytek.skillhub.workbench.port.AgentRuntimePort;
import com.iflytek.skillhub.workbench.port.AgentRuntimeRunCommand;
import com.iflytek.skillhub.workbench.port.AgentRuntimeRunHandle;
import com.iflytek.skillhub.workbench.port.AgentRuntimeRunStatus;
import com.iflytek.skillhub.workbench.port.AgentRuntimeStreamListener;
import com.iflytek.skillhub.workbench.port.AgentRuntimeWorkspaceFile;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.filesystem.local.LocalFilesystemWithShell;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AgentScopeRuntimeAdapter implements AgentRuntimePort {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> REQUIRED_BUILTIN_TOOLS = Set.of(
            "read_file", "write_file", "edit_file", "grep_files", "glob_files", "list_files", "execute");
    private static final int SHELL_MAX_OUTPUT_BYTES = 1024 * 1024;

    private final AgentScopeSessionWorkspaceResolver workspaceResolver;
    private final ModelExecutorSettings modelSettings;
    private final ModelExecutor modelExecutor;
    private final String systemPrompt;
    private final ConcurrentMap<String, ActiveRun> activeRuns = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, String> activeSessionRunIds = new ConcurrentHashMap<>();

    @Autowired
    public AgentScopeRuntimeAdapter(
            @Value("${skillhub.workbench.runtime.workspace-base-path:${java.io.tmpdir}/skillhub-workbench-runtime}")
            String workspaceBasePath,
            @Value("${skillhub.workbench.runtime.model-executor-enabled:false}") boolean modelExecutorEnabled,
            @Value("${skillhub.workbench.runtime.model-provider:}") String modelProvider,
            @Value("${skillhub.workbench.runtime.model-name:}") String modelName,
            @Value("${skillhub.workbench.runtime.model-base-url:}") String modelBaseUrl,
            @Value("${skillhub.workbench.runtime.model-api-key:}") String modelApiKey,
            @Value("${skillhub.workbench.runtime.model-request-timeout-seconds:180}") int modelRequestTimeoutSeconds,
            WorkbenchBuiltinSkillPromptProvider promptProvider) {
        this(Path.of(workspaceBasePath), new ModelExecutorSettings(
                modelExecutorEnabled,
                modelProvider,
                modelName,
                modelBaseUrl,
                modelApiKey,
                modelRequestTimeoutSeconds), null, promptProvider.systemPrompt());
    }

    AgentScopeRuntimeAdapter(Path workspaceBaseRoot) {
        this(workspaceBaseRoot, ModelExecutorSettings.disabled(), null);
    }

    AgentScopeRuntimeAdapter(Path workspaceBaseRoot, ModelExecutorSettings modelSettings, ModelExecutor modelExecutor) {
        this(workspaceBaseRoot, modelSettings, modelExecutor, WorkbenchBuiltinSkillPromptProvider.BASE_SYSTEM_PROMPT);
    }

    AgentScopeRuntimeAdapter(
            Path workspaceBaseRoot,
            ModelExecutorSettings modelSettings,
            ModelExecutor modelExecutor,
            String systemPrompt) {
        this.workspaceResolver = new AgentScopeSessionWorkspaceResolver(workspaceBaseRoot);
        this.modelSettings = Objects.requireNonNull(modelSettings, "modelSettings").normalized();
        this.systemPrompt = systemPrompt == null || systemPrompt.isBlank()
                ? WorkbenchBuiltinSkillPromptProvider.BASE_SYSTEM_PROMPT
                : systemPrompt;
        this.modelExecutor = modelExecutor != null
                ? modelExecutor
                : this.modelSettings.configured()
                        ? new HarnessModelExecutor(this.modelSettings, this.systemPrompt)
                        : null;
    }

    @Override
    public AgentRuntimeRunHandle start(AgentRuntimeRunCommand command) {
        return startInternal(command, null);
    }

    @Override
    public AgentRuntimeRunHandle startStreaming(AgentRuntimeRunCommand command, AgentRuntimeStreamListener listener) {
        return startInternal(command, listener);
    }

    private AgentRuntimeRunHandle startInternal(AgentRuntimeRunCommand command, AgentRuntimeStreamListener listener) {
        RuntimeContext context = createContext(command.userId(), command.sessionId());
        Path workspaceRoot = workspaceResolver.resolve(command.userId(), String.valueOf(command.sessionId()));
        String runId = command.sessionId() + "-" + UUID.randomUUID();

        if (!modelSettings.configured() || modelExecutor == null) {
            AgentRuntimeEvent event = AgentRuntimeEvent.modelMessage(
                    "已记录你的消息；模型执行器未配置，当前不会调用真实模型。");
            if (listener != null) {
                listener.onRunStarted(runId);
                listener.onRuntimeEvent(event);
            }
            return new AgentRuntimeRunHandle(
                    runId,
                    AgentRuntimeRunStatus.COMPLETED,
                    "模型执行器未配置",
                    List.of(event));
        }

        String previousRunId = activeSessionRunIds.put(command.sessionId(), runId);
        if (previousRunId != null) {
            activeRuns.remove(previousRunId);
        }
        Instant startedAt = Instant.now();
        activeRuns.put(runId, new ActiveRun(command.userId(), command.sessionId(), context, workspaceRoot, startedAt));
        List<AgentRuntimeEvent> events = new ArrayList<>();
        try {
            materializeWorkspace(workspaceRoot, command.workspaceFiles());
            if (listener != null) {
                listener.onRunStarted(runId);
            }
            Consumer<AgentRuntimeEvent> runtimeEventConsumer = event -> {
                events.add(event);
                if (listener != null) {
                    listener.onRuntimeEvent(event);
                }
            };
            String response = modelExecutor.execute(
                    command,
                    workspaceRoot,
                    context,
                    listener == null ? null : listener::onDelta,
                    runtimeEventConsumer);
            workspaceChanges(workspaceRoot, command.workspaceFiles()).forEach(runtimeEventConsumer);
            AgentRuntimeEvent modelEvent = AgentRuntimeEvent.modelMessage(response);
            runtimeEventConsumer.accept(modelEvent);
            return new AgentRuntimeRunHandle(
                    runId,
                    AgentRuntimeRunStatus.COMPLETED,
                    "模型已响应",
                    List.copyOf(events));
        } catch (RuntimeException ex) {
            String message = "模型调用失败：" + sanitizeError(ex.getMessage());
            AgentRuntimeEvent event = AgentRuntimeEvent.error(message);
            events.add(event);
            if (listener != null) {
                listener.onRuntimeEvent(event);
            }
            return new AgentRuntimeRunHandle(
                    runId,
                    AgentRuntimeRunStatus.FAILED,
                    message,
                    List.copyOf(events));
        } finally {
            activeRuns.remove(runId);
            activeSessionRunIds.remove(command.sessionId(), runId);
        }
    }

    @Override
    public AgentRuntimeRunHandle cancel(AgentRuntimeCancelCommand command) {
        ActiveRun activeRun = activeRuns.get(command.runId());
        if (activeRun == null) {
            return new AgentRuntimeRunHandle(
                    command.runId(),
                    AgentRuntimeRunStatus.REJECTED,
                    "运行任务不存在或已结束",
                    List.of(AgentRuntimeEvent.error("运行任务不存在或已结束。")));
        }
        if (!activeRun.userId().equals(command.userId()) || !activeRun.sessionId().equals(command.sessionId())) {
            return new AgentRuntimeRunHandle(
                    command.runId(),
                    AgentRuntimeRunStatus.REJECTED,
                    "无权取消该运行任务",
                    List.of(AgentRuntimeEvent.error("运行任务不属于当前会话。")));
        }
        activeRuns.remove(command.runId(), activeRun);
        activeSessionRunIds.remove(command.sessionId(), command.runId());
        return new AgentRuntimeRunHandle(
                command.runId(),
                AgentRuntimeRunStatus.CANCELLED,
                "运行已取消",
                List.of(AgentRuntimeEvent.modelMessage("运行任务已取消。")));
    }

    @Override
    public Optional<AgentRuntimeRunHandle> findActiveRun(String userId, Long sessionId) {
        String runId = activeSessionRunIds.get(sessionId);
        if (runId == null) {
            return Optional.empty();
        }
        ActiveRun activeRun = activeRuns.get(runId);
        if (activeRun == null || !activeRun.userId().equals(userId) || !activeRun.sessionId().equals(sessionId)) {
            return Optional.empty();
        }
        return Optional.of(new AgentRuntimeRunHandle(
                runId, AgentRuntimeRunStatus.STARTED, "模型正在回复", List.of(), activeRun.startedAt()));
    }

    RuntimeContext createContext(String userId, Long sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        return RuntimeContext.builder()
                .userId(userId)
                .sessionId(String.valueOf(sessionId))
                .build();
    }

    Path workspaceRoot(String userId, Long sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        return workspaceResolver.resolve(userId, String.valueOf(sessionId));
    }

    Map<String, ActiveRun> activeRuns() {
        return Map.copyOf(activeRuns);
    }

    String systemPrompt() {
        return systemPrompt;
    }

    private static void materializeWorkspace(Path workspaceRoot, List<AgentRuntimeWorkspaceFile> files) {
        try (Stream<Path> paths = Files.walk(workspaceRoot)) {
            paths.sorted(Comparator.reverseOrder())
                    .filter(path -> !path.equals(workspaceRoot))
                    .forEach(AgentScopeRuntimeAdapter::deletePath);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to reset runtime workspace", ex);
        }
        for (AgentRuntimeWorkspaceFile file : files) {
            Path target = safeResolve(workspaceRoot, file.path());
            try {
                Files.createDirectories(target.getParent());
                Files.write(target, file.content());
            } catch (IOException ex) {
                throw new UncheckedIOException("Failed to materialize runtime file: " + file.path(), ex);
            }
        }
    }

    private static List<AgentRuntimeEvent> workspaceChanges(
            Path workspaceRoot,
            List<AgentRuntimeWorkspaceFile> baselineFiles) {
        Map<String, AgentRuntimeWorkspaceFile> baseline = new LinkedHashMap<>();
        baselineFiles.forEach(file -> baseline.put(file.path(), file));
        Map<String, byte[]> current = readWorkspaceFiles(workspaceRoot);
        List<AgentRuntimeEvent> events = new ArrayList<>();
        current.entrySet().stream()
                .filter(entry -> !baseline.containsKey(entry.getKey())
                        || !Arrays.equals(baseline.get(entry.getKey()).content(), entry.getValue()))
                .forEach(entry -> events.add(AgentRuntimeEvent.fileWritten(
                        entry.getKey(),
                        entry.getValue(),
                        contentType(entry.getKey(), baseline.get(entry.getKey())))));
        baseline.keySet().stream()
                .filter(path -> !current.containsKey(path))
                .sorted()
                .forEach(path -> events.add(AgentRuntimeEvent.fileDeleted(path)));
        return List.copyOf(events);
    }

    private static Map<String, byte[]> readWorkspaceFiles(Path workspaceRoot) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        long totalBytes = 0L;
        try (Stream<Path> paths = Files.walk(workspaceRoot)) {
            for (Path path : paths
                    .filter(candidate -> Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS))
                    .sorted()
                    .toList()) {
                Path realPath = path.toRealPath(LinkOption.NOFOLLOW_LINKS);
                if (!realPath.startsWith(workspaceRoot.toRealPath(LinkOption.NOFOLLOW_LINKS))) {
                    throw new IllegalStateException("Runtime workspace file escaped workspace root");
                }
                String relativePath = workspaceRoot.relativize(path).toString().replace('\\', '/');
                if (relativePath.equals(".agentscope") || relativePath.startsWith(".agentscope/")) {
                    continue;
                }
                long size = Files.size(path);
                if (size > SkillPackagePolicy.MAX_SINGLE_FILE_SIZE) {
                    throw new IllegalStateException("Runtime file exceeds max single file size: " + relativePath);
                }
                totalBytes += size;
                if (totalBytes > SkillPackagePolicy.MAX_TOTAL_PACKAGE_SIZE) {
                    throw new IllegalStateException("Runtime workspace exceeds max package size");
                }
                if (files.size() >= SkillPackagePolicy.MAX_FILE_COUNT) {
                    throw new IllegalStateException("Runtime workspace exceeds max file count");
                }
                files.put(relativePath, Files.readAllBytes(path));
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read runtime workspace", ex);
        }
        return files;
    }

    private static String contentType(String path, AgentRuntimeWorkspaceFile baseline) {
        if (baseline != null) {
            return baseline.contentType();
        }
        try {
            String detected = Files.probeContentType(Path.of(path));
            return detected == null || detected.isBlank() ? "application/octet-stream" : detected;
        } catch (IOException ex) {
            return "application/octet-stream";
        }
    }

    private static Path safeResolve(Path workspaceRoot, String relativePath) {
        Path target = workspaceRoot.resolve(relativePath).normalize();
        if (!target.startsWith(workspaceRoot)) {
            throw new IllegalArgumentException("Runtime file escaped workspace root: " + relativePath);
        }
        return target;
    }

    private static void deletePath(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to clear runtime workspace: " + path.getFileName(), ex);
        }
    }

    record ActiveRun(String userId, Long sessionId, RuntimeContext context, Path workspaceRoot, Instant startedAt) {
    }

    record ModelExecutorSettings(
            boolean enabled,
            String provider,
            String modelName,
            String baseUrl,
            String apiKey,
            int timeoutSeconds) {

        static ModelExecutorSettings disabled() {
            return new ModelExecutorSettings(false, null, null, null, null, 180);
        }

        ModelExecutorSettings normalized() {
            return new ModelExecutorSettings(
                    enabled,
                    normalize(provider),
                    normalize(modelName),
                    normalize(baseUrl),
                    normalize(apiKey),
                    normalizeTimeoutSeconds(timeoutSeconds));
        }

        boolean configured() {
            return enabled
                    && provider != null
                    && modelName != null
                    && baseUrl != null
                    && apiKey != null;
        }

        private static String normalize(String value) {
            return value == null || value.isBlank() ? null : value.trim();
        }

        private static int normalizeTimeoutSeconds(int value) {
            return Math.max(30, Math.min(value, 600));
        }
    }

    interface ModelExecutor {
        String complete(AgentRuntimeRunCommand command);

        default String completeStreaming(AgentRuntimeRunCommand command, BiConsumer<String, String> deltaConsumer) {
            String response = complete(command);
            if (deltaConsumer != null && response != null && !response.isBlank()) {
                deltaConsumer.accept("final", response);
            }
            return response;
        }

        default String execute(
                AgentRuntimeRunCommand command,
                Path workspaceRoot,
                RuntimeContext context,
                BiConsumer<String, String> deltaConsumer,
                Consumer<AgentRuntimeEvent> eventConsumer) {
            return completeStreaming(command, deltaConsumer);
        }
    }

    static final class HarnessModelExecutor implements ModelExecutor {
        private final ModelExecutorSettings settings;
        private final String systemPrompt;

        HarnessModelExecutor(ModelExecutorSettings settings, String systemPrompt) {
            this.settings = settings;
            this.systemPrompt = systemPrompt;
        }

        @Override
        public String complete(AgentRuntimeRunCommand command) {
            throw new UnsupportedOperationException("Harness execution requires a runtime workspace");
        }

        @Override
        public String execute(
                AgentRuntimeRunCommand command,
                Path workspaceRoot,
                RuntimeContext context,
                BiConsumer<String, String> deltaConsumer,
                Consumer<AgentRuntimeEvent> eventConsumer) {
            Toolkit toolkit = new Toolkit();
            List<McpClientWrapper> mcpClients = registerMcpClients(toolkit, command.mcpServers());
            try {
                LocalFilesystemWithShell filesystem = new LocalFilesystemWithShell(
                        workspaceRoot,
                        true,
                        settings.timeoutSeconds(),
                        SHELL_MAX_OUTPUT_BYTES,
                        Map.of(),
                        false);
                OpenAIChatModel model = OpenAIChatModel.builder()
                        .apiKey(settings.apiKey())
                        .baseUrl(settings.baseUrl())
                        .modelName(settings.modelName())
                        .stream(true)
                        .build();
                try (HarnessAgent agent = HarnessAgent.builder()
                        .name("skillhub-workbench")
                        .agentId("skillhub-workbench-" + UUID.randomUUID())
                        .description("SkillHub workbench agent")
                        .model(model)
                        .toolkit(toolkit)
                        .workspace(workspaceRoot)
                        .abstractFilesystem(filesystem)
                        .sysPrompt(systemPromptFor(command))
                        .permissionContext(PermissionContextState.builder()
                                .mode(PermissionMode.BYPASS)
                                .build())
                        .stateStore(new InMemoryAgentStateStore())
                        .disableSubagents()
                        .disableDynamicSubagents()
                        .disableMemoryTools()
                        .disableMemoryHooks()
                        .disableCompaction()
                        .disableToolResultEviction()
                        .disableDynamicSkills()
                        .disableDefaultWorkspaceSkills()
                        .disableToolsConfig()
                        .disableWorkspaceContext()
                        .disableAtPathExpansion()
                        .disableSessionPersistence()
                        .build()) {
                    assertBuiltinTools(agent.getToolkit());
                    StringBuilder finalContent = new StringBuilder();
                    agent.streamEvents(command.message(), context)
                            .doOnNext(event -> handleEvent(event, deltaConsumer, eventConsumer, finalContent))
                            .blockLast(Duration.ofSeconds(settings.timeoutSeconds()));
                    if (finalContent.isEmpty()) {
                        throw new IllegalStateException("模型服务响应为空");
                    }
                    return finalContent.toString();
                }
            } finally {
                mcpClients.forEach(HarnessModelExecutor::closeClient);
            }
        }

        private List<McpClientWrapper> registerMcpClients(
                Toolkit toolkit,
                List<AgentRuntimeMcpServer> servers) {
            List<McpClientWrapper> clients = new ArrayList<>();
            for (AgentRuntimeMcpServer server : servers) {
                try {
                    McpClientBuilder builder = McpClientBuilder.create(server.serverId())
                            .timeout(Duration.ofSeconds(settings.timeoutSeconds()))
                            .initializationTimeout(Duration.ofSeconds(Math.min(settings.timeoutSeconds(), 60)));
                    if (server.transport().equals("sse")) {
                        builder.sseTransport(server.url());
                    } else {
                        builder.streamableHttpTransport(server.url());
                    }
                    McpClientWrapper client = builder.buildSync();
                    clients.add(client);
                    Toolkit.ToolRegistration registration = toolkit.registration().mcpClient(client);
                    if (!server.enabledTools().isEmpty()) {
                        registration.enableTools(server.enabledTools());
                    }
                    if (!server.disabledTools().isEmpty()) {
                        registration.disableTools(server.disabledTools());
                    }
                    registration.apply();
                } catch (RuntimeException ex) {
                    clients.forEach(HarnessModelExecutor::closeClient);
                    throw new IllegalStateException(
                            "MCP 服务连接失败 [" + server.serverId() + "]: " + sanitizeError(ex.getMessage()), ex);
                }
            }
            return clients;
        }

        private String systemPromptFor(AgentRuntimeRunCommand command) {
            if (command.sessionContext().isBlank()) {
                return systemPrompt;
            }
            return systemPrompt
                    + "\n\n<workbench-session-context>\n"
                    + command.sessionContext()
                    + "\n</workbench-session-context>";
        }

        private static void assertBuiltinTools(Toolkit toolkit) {
            if (!toolkit.getToolNames().containsAll(REQUIRED_BUILTIN_TOOLS)) {
                Set<String> missing = new java.util.LinkedHashSet<>(REQUIRED_BUILTIN_TOOLS);
                missing.removeAll(toolkit.getToolNames());
                throw new IllegalStateException("Harness 内置工具未注册: " + missing);
            }
        }

        private static void handleEvent(
                AgentEvent event,
                BiConsumer<String, String> deltaConsumer,
                Consumer<AgentRuntimeEvent> eventConsumer,
                StringBuilder finalContent) {
            if (event instanceof ThinkingBlockDeltaEvent thinking) {
                emitDelta(deltaConsumer, "thinking", thinking.getDelta());
            } else if (event instanceof TextBlockDeltaEvent text) {
                emitDelta(deltaConsumer, "final", text.getDelta());
            } else if (event instanceof ToolCallStartEvent toolCall) {
                eventConsumer.accept(new AgentRuntimeEvent(
                        com.iflytek.skillhub.workbench.port.AgentRuntimeEventType.TOOL_CALL,
                        json(Map.of(
                                "toolCallId", toolCall.getToolCallId(),
                                "toolName", toolCall.getToolCallName(),
                                "status", "started")),
                        null,
                        null,
                        null));
            } else if (event instanceof RequireUserConfirmEvent confirmation) {
                for (ToolUseBlock toolCall : confirmation.getToolCalls()) {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("toolCallId", toolCall.getId());
                    payload.put("toolName", toolCall.getName());
                    payload.put("arguments", toolCall.getInput());
                    payload.put("risk", "MEDIUM");
                    eventConsumer.accept(new AgentRuntimeEvent(
                            com.iflytek.skillhub.workbench.port.AgentRuntimeEventType.APPROVAL_REQUIRED,
                            json(payload),
                            null,
                            null,
                            null));
                }
            } else if (event instanceof AgentResultEvent result
                    && result.getResult() != null
                    && result.getResult().getTextContent() != null) {
                finalContent.setLength(0);
                finalContent.append(result.getResult().getTextContent());
            }
        }

        private static void emitDelta(BiConsumer<String, String> consumer, String phase, String content) {
            if (consumer != null && content != null && !content.isEmpty()) {
                consumer.accept(phase, content);
            }
        }

        private static void closeClient(McpClientWrapper client) {
            try {
                client.close();
            } catch (RuntimeException ignored) {
                // The run result is already known; client shutdown must not replace it.
            }
        }
    }

    private static String json(Map<String, ?> value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize runtime event", ex);
        }
    }

    private static String sanitizeError(String message) {
        if (message == null || message.isBlank()) {
            return "未知错误";
        }
        return message
                .replaceAll("(?i)bearer\\s+[A-Za-z0-9._\\-]+", "Bearer [REDACTED]")
                .replaceAll("sk-[A-Za-z0-9._\\-]+", "sk-[REDACTED]");
    }
}
