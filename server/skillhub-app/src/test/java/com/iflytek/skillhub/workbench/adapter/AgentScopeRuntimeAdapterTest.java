package com.iflytek.skillhub.workbench.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.workbench.port.AgentRuntimeCancelCommand;
import com.iflytek.skillhub.workbench.port.AgentRuntimeEvent;
import com.iflytek.skillhub.workbench.port.AgentRuntimeEventType;
import com.iflytek.skillhub.workbench.port.AgentRuntimeMcpServer;
import com.iflytek.skillhub.workbench.port.AgentRuntimeRunCommand;
import com.iflytek.skillhub.workbench.port.AgentRuntimeRunHandle;
import com.iflytek.skillhub.workbench.port.AgentRuntimeRunStatus;
import com.iflytek.skillhub.workbench.port.AgentRuntimeStreamListener;
import com.iflytek.skillhub.workbench.port.AgentRuntimeWorkspaceFile;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import io.agentscope.core.agent.RuntimeContext;
import java.lang.reflect.Field;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class AgentScopeRuntimeAdapterTest {

    @TempDir
    private Path tempDir;

    @Test
    void mapsSkillHubUserAndSessionIntoAgentScopeRuntimeContext() {
        AgentScopeRuntimeAdapter adapter = new AgentScopeRuntimeAdapter(tempDir);

        RuntimeContext context = adapter.createContext("user-1", 7L);

        assertThat(context.getUserId()).isEqualTo("user-1");
        assertThat(context.getSessionId()).isEqualTo("7");
    }

    @Test
    void createsIsolatedRuntimeWorkspacePerSession() {
        AgentScopeRuntimeAdapter adapter = new AgentScopeRuntimeAdapter(tempDir);

        Path first = adapter.workspaceRoot("user-1", 7L);
        Path second = adapter.workspaceRoot("user-1", 8L);

        assertThat(first).isNotEqualTo(second);
        assertThat(first).startsWith(tempDir.toAbsolutePath().normalize());
        assertThat(second).startsWith(tempDir.toAbsolutePath().normalize());
        assertThat(Files.isDirectory(first)).isTrue();
        assertThat(Files.isDirectory(second)).isTrue();
    }

    @Test
    void startWithoutModelSettingsRecordsMessageWithoutLeavingActiveRun() {
        AgentScopeRuntimeAdapter adapter = new AgentScopeRuntimeAdapter(tempDir);

        AgentRuntimeRunHandle handle = adapter.start(new AgentRuntimeRunCommand(
                "user-1",
                7L,
                "workbench/user/session",
                "create a skill"));

        assertThat(handle.status()).isEqualTo(AgentRuntimeRunStatus.COMPLETED);
        assertThat(handle.message()).isEqualTo("模型执行器未配置");
        assertThat(handle.events().getFirst().payloadJson()).contains("模型执行器未配置");
        assertThat(handle.events()).hasSize(1);
        assertThat(adapter.activeRuns()).isEmpty();
    }

    @Test
    void startUsesConfiguredModelExecutorWhenModelSettingsAreComplete() {
        AgentScopeRuntimeAdapter adapter = new AgentScopeRuntimeAdapter(
                tempDir,
                new AgentScopeRuntimeAdapter.ModelExecutorSettings(
                        true,
                        "openai-compatible",
                        "qwen3.7-max",
                        "https://example.com",
                        "secret-key",
                        180),
                command -> "你好，我已接收到你的消息。");

        AgentRuntimeRunHandle handle = adapter.start(new AgentRuntimeRunCommand(
                "user-1",
                7L,
                "workbench/user/session",
                "nihao"));

        assertThat(handle.status()).isEqualTo(AgentRuntimeRunStatus.COMPLETED);
        assertThat(handle.message()).isEqualTo("模型已响应");
        assertThat(handle.events()).hasSize(1);
        assertThat(handle.events().getFirst().payloadJson()).contains("你好，我已接收到你的消息。");
        assertThat(handle.events().getFirst().payloadJson()).doesNotContain("模型执行器未配置");
        assertThat(adapter.activeRuns()).isEmpty();
    }

    @Test
    void startMaterializesCurrentFilesAndReturnsRuntimeFileChanges() {
        Path expectedWorkspace = new AgentScopeSessionWorkspaceResolver(tempDir).resolve("user-1", "7");
        AgentScopeRuntimeAdapter adapter = new AgentScopeRuntimeAdapter(
                tempDir,
                new AgentScopeRuntimeAdapter.ModelExecutorSettings(
                        true,
                        "openai-compatible",
                        "qwen3.7-max",
                        "https://example.com",
                        "secret-key",
                180),
                command -> {
                    try {
                        assertThat(Files.readString(expectedWorkspace.resolve("SKILL.md")))
                                .isEqualTo("# Existing skill");
                        Files.writeString(expectedWorkspace.resolve("SKILL.md"), "# Updated skill");
                        Files.writeString(expectedWorkspace.resolve("notes.md"), "runtime note");
                        return "# Done";
                    } catch (java.io.IOException ex) {
                        throw new IllegalStateException(ex);
                    }
                });

        AgentRuntimeRunHandle handle = adapter.start(new AgentRuntimeRunCommand(
                "user-1",
                7L,
                "workbench/user/session",
                "",
                List.of(),
                List.of(new AgentRuntimeWorkspaceFile(
                        "SKILL.md",
                        "# Existing skill".getBytes(StandardCharsets.UTF_8),
                        "text/markdown")),
                "update the skill"));

        assertThat(handle.status()).isEqualTo(AgentRuntimeRunStatus.COMPLETED);
        assertThat(handle.events())
                .extracting(event -> event.type())
                .containsExactly(
                        AgentRuntimeEventType.FILE_WRITTEN,
                        AgentRuntimeEventType.FILE_WRITTEN,
                        AgentRuntimeEventType.MODEL_MESSAGE);
        assertThat(handle.events())
                .filteredOn(event -> event.type() == AgentRuntimeEventType.FILE_WRITTEN)
                .extracting(event -> event.filePath())
                .containsExactly("SKILL.md", "notes.md");
    }

    @Test
    void adapterKeepsConfiguredSystemPromptForModelExecutor() {
        AgentScopeRuntimeAdapter adapter = new AgentScopeRuntimeAdapter(
                tempDir,
                new AgentScopeRuntimeAdapter.ModelExecutorSettings(
                        true,
                        "openai-compatible",
                        "qwen3.7-max",
                        "https://example.com",
                        "secret-key",
                        180),
                command -> "done",
                "custom skill creator prompt");

        assertThat(adapter.systemPrompt()).isEqualTo("custom skill creator prompt");
    }

    @Test
    void configuredModelRequestIncludesWorkbenchSessionContextInSystemMessage() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = """
                    data: {"id":"chatcmpl-1","object":"chat.completion.chunk","created":1,"model":"qwen3.7-max","choices":[{"index":0,"delta":{"role":"assistant","content":"done"},"finish_reason":null}]}

                    data: {"id":"chatcmpl-1","object":"chat.completion.chunk","created":1,"model":"qwen3.7-max","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}

                    data: [DONE]

                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            AgentScopeRuntimeAdapter adapter = new AgentScopeRuntimeAdapter(
                    tempDir,
                    new AgentScopeRuntimeAdapter.ModelExecutorSettings(
                            true,
                            "openai-compatible",
                            "qwen3.7-max",
                            "http://127.0.0.1:" + server.getAddress().getPort(),
                            "secret-key",
                            180),
                    null,
                    "base prompt");

            AgentRuntimeRunHandle handle = adapter.start(new AgentRuntimeRunCommand(
                    "user-1",
                    7L,
                    "workbench/user/session",
                    "目标技能标识: report-helper\n目标版本: 0.1.0",
                    "你知道当前技能叫什么吗"));

            assertThat(handle.status()).as(handle.message()).isEqualTo(AgentRuntimeRunStatus.COMPLETED);
            JsonNode systemContent = new ObjectMapper().readTree(requestBody.get())
                    .path("messages")
                    .path(0)
                    .path("content");
            assertThat(systemContent.asText())
                    .contains("base prompt")
                    .contains("<workbench-session-context>")
                    .contains("目标技能标识: report-helper")
                    .contains("目标版本: 0.1.0");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void harnessExecutesSelectedMcpToolAndExposesBuiltinToolsToModel() throws Exception {
        ObjectMapper json = new ObjectMapper();
        AtomicInteger mcpToolCalls = new AtomicInteger();
        AtomicInteger modelCalls = new AtomicInteger();
        AtomicReference<JsonNode> firstModelRequest = new AtomicReference<>();
        HttpServer mcpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        mcpServer.createContext("/mcp", exchange -> {
            JsonNode request = json.readTree(exchange.getRequestBody());
            String method = request.path("method").asText();
            if (!request.has("id")) {
                exchange.sendResponseHeaders(202, -1);
                exchange.close();
                return;
            }
            String id = request.path("id").toString();
            String result;
            if (method.equals("initialize")) {
                String protocolVersion = request.path("params").path("protocolVersion").asText("2024-11-05");
                result = "{\"protocolVersion\":" + json.writeValueAsString(protocolVersion)
                        + ",\"capabilities\":{\"tools\":{}},"
                        + "\"serverInfo\":{\"name\":\"test-mcp\",\"version\":\"1.0\"}}";
            } else if (method.equals("tools/list")) {
                result = "{\"tools\":[{\"name\":\"echo\",\"description\":\"Echo text\","
                        + "\"inputSchema\":{\"type\":\"object\",\"properties\":{\"text\":{\"type\":\"string\"}},"
                        + "\"required\":[\"text\"]}}]}";
            } else if (method.equals("tools/call")) {
                mcpToolCalls.incrementAndGet();
                String text = request.path("params").path("arguments").path("text").asText();
                result = "{\"content\":[{\"type\":\"text\",\"text\":"
                        + json.writeValueAsString("echo: " + text) + "}],\"isError\":false}";
            } else {
                sendJson(exchange, 400, "{\"jsonrpc\":\"2.0\",\"id\":" + id
                        + ",\"error\":{\"code\":-32601,\"message\":\"unknown method\"}}");
                return;
            }
            sendJson(exchange, 200, "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":" + result + "}");
        });

        HttpServer modelServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        modelServer.createContext("/v1/chat/completions", exchange -> {
            JsonNode request = json.readTree(exchange.getRequestBody());
            int call = modelCalls.incrementAndGet();
            if (call == 1) {
                firstModelRequest.set(request);
                String toolName = "echo";
                for (JsonNode tool : request.path("tools")) {
                    String candidate = tool.path("function").path("name").asText();
                    if (candidate.equals("echo") || candidate.endsWith("__echo")) {
                        toolName = candidate;
                        break;
                    }
                }
                sendSse(exchange, """
                        data: {"id":"chatcmpl-tool","object":"chat.completion.chunk","created":1,"model":"qwen3.7-max","choices":[{"index":0,"delta":{"role":"assistant","tool_calls":[{"index":0,"id":"call-1","type":"function","function":{"name":"%s","arguments":"{\\\"text\\\":\\\"hello\\\"}"}}]},"finish_reason":null}]}

                        data: {"id":"chatcmpl-tool","object":"chat.completion.chunk","created":1,"model":"qwen3.7-max","choices":[{"index":0,"delta":{},"finish_reason":"tool_calls"}]}

                        data: [DONE]

                        """.formatted(toolName));
            } else {
                sendSse(exchange, """
                        data: {"id":"chatcmpl-final","object":"chat.completion.chunk","created":1,"model":"qwen3.7-max","choices":[{"index":0,"delta":{"role":"assistant","content":"MCP result received"},"finish_reason":null}]}

                        data: {"id":"chatcmpl-final","object":"chat.completion.chunk","created":1,"model":"qwen3.7-max","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}

                        data: [DONE]

                        """);
            }
        });
        mcpServer.start();
        modelServer.start();
        try {
            AgentScopeRuntimeAdapter adapter = new AgentScopeRuntimeAdapter(
                    tempDir,
                    new AgentScopeRuntimeAdapter.ModelExecutorSettings(
                            true,
                            "openai-compatible",
                            "qwen3.7-max",
                            "http://127.0.0.1:" + modelServer.getAddress().getPort(),
                            "secret-key",
                            60),
                    null,
                    "Use the selected tools when needed.");
            AgentRuntimeRunCommand command = new AgentRuntimeRunCommand(
                    "user-1",
                    7L,
                    "workbench/user/session",
                    "",
                    List.of(new AgentRuntimeMcpServer(
                            "test-mcp",
                            "http",
                            "http://127.0.0.1:" + mcpServer.getAddress().getPort() + "/mcp",
                            List.of("echo"),
                            List.of())),
                    List.of(),
                    "Call echo with hello, then report the result.");
            List<String> deltas = new ArrayList<>();
            List<AgentRuntimeEvent> streamedEvents = new ArrayList<>();
            AgentRuntimeRunHandle handle = adapter.startStreaming(command, new AgentRuntimeStreamListener() {
                @Override
                public void onRunStarted(String runId) {
                }

                @Override
                public void onDelta(String phase, String content) {
                    deltas.add(phase + ":" + content);
                }

                @Override
                public void onRuntimeEvent(AgentRuntimeEvent event) {
                    streamedEvents.add(event);
                }
            });

            assertThat(handle.status()).as(handle.message()
                            + "; mcpCalls=" + mcpToolCalls.get()
                            + "; modelCalls=" + modelCalls.get()
                            + "; events=" + handle.events())
                    .isEqualTo(AgentRuntimeRunStatus.COMPLETED);
            assertThat(handle.events()).extracting(event -> event.type())
                    .contains(AgentRuntimeEventType.TOOL_CALL, AgentRuntimeEventType.MODEL_MESSAGE);
            assertThat(handle.events().getLast().payloadJson()).contains("MCP result received");
            assertThat(deltas).anySatisfy(delta -> assertThat(delta).contains("final:MCP result received"));
            assertThat(streamedEvents).extracting(AgentRuntimeEvent::type)
                    .contains(AgentRuntimeEventType.TOOL_CALL, AgentRuntimeEventType.MODEL_MESSAGE);
            assertThat(mcpToolCalls).hasValue(1);
            assertThat(modelCalls).hasValue(2);
            assertThat(toolNames(firstModelRequest.get()))
                    .contains("read_file", "write_file", "execute", "echo");
        } finally {
            modelServer.stop(0);
            mcpServer.stop(0);
        }
    }

    @Test
    void workbenchBuiltinSkillPromptLoadsSkillCreatorInstructions() {
        String prompt = new WorkbenchBuiltinSkillPromptProvider().systemPrompt();

        assertThat(prompt).contains("你内置了 skill-creator 技能");
        assertThat(prompt).contains("workbench/builtin-skills/skill-creator/SKILL.md");
        assertThat(prompt).contains("skill-creator");
    }

    @Test
    void cancelRemovesActiveRunAndRejectsUnknownRun() {
        AgentScopeRuntimeAdapter adapter = new AgentScopeRuntimeAdapter(tempDir);
        String runId = "run-1";
        insertActiveRun(adapter, runId, "user-1", 7L);

        AgentRuntimeRunHandle cancelled = adapter.cancel(new AgentRuntimeCancelCommand(
                "user-1",
                7L,
                runId));
        AgentRuntimeRunHandle missing = adapter.cancel(new AgentRuntimeCancelCommand(
                "user-1",
                7L,
                runId));

        assertThat(cancelled.status()).isEqualTo(AgentRuntimeRunStatus.CANCELLED);
        assertThat(adapter.activeRuns()).doesNotContainKey(runId);
        assertThat(missing.status()).isEqualTo(AgentRuntimeRunStatus.REJECTED);
        assertThat(missing.events()).hasSize(1);
    }

    @Test
    void cancelRejectsRunOwnedByDifferentSession() {
        AgentScopeRuntimeAdapter adapter = new AgentScopeRuntimeAdapter(tempDir);
        String runId = "run-1";
        insertActiveRun(adapter, runId, "user-1", 7L);

        AgentRuntimeRunHandle rejected = adapter.cancel(new AgentRuntimeCancelCommand(
                "user-1",
                8L,
                runId));

        assertThat(rejected.status()).isEqualTo(AgentRuntimeRunStatus.REJECTED);
        assertThat(adapter.activeRuns()).containsKey(runId);
    }

    @Test
    void findActiveRunReturnsOnlyMatchingUserAndSession() {
        AgentScopeRuntimeAdapter adapter = new AgentScopeRuntimeAdapter(tempDir);
        insertActiveRun(adapter, "run-1", "user-1", 7L);

        assertThat(adapter.findActiveRun("user-1", 7L))
                .isPresent()
                .get()
                .satisfies(run -> {
                    assertThat(run.runId()).isEqualTo("run-1");
                    assertThat(run.status()).isEqualTo(AgentRuntimeRunStatus.STARTED);
                });
        assertThat(adapter.findActiveRun("user-2", 7L)).isEmpty();
        assertThat(adapter.findActiveRun("user-1", 8L)).isEmpty();
    }

    @Test
    void configuredModelCompletionDoesNotLeaveActiveRunBehind() {
        AgentScopeRuntimeAdapter adapter = new AgentScopeRuntimeAdapter(
                tempDir,
                new AgentScopeRuntimeAdapter.ModelExecutorSettings(
                        true,
                        "openai-compatible",
                        "qwen3.7-max",
                        "https://example.com",
                        "secret-key",
                        180),
                command -> "done");
        AgentRuntimeRunCommand command = new AgentRuntimeRunCommand(
                "user-1",
                7L,
                "workbench/user/session",
                "create a skill");

        AgentRuntimeRunHandle first = adapter.start(command);
        AgentRuntimeRunHandle second = adapter.start(command);

        assertThat(first.status()).isEqualTo(AgentRuntimeRunStatus.COMPLETED);
        assertThat(second.status()).isEqualTo(AgentRuntimeRunStatus.COMPLETED);
        assertThat(adapter.activeRuns()).doesNotContainKey(first.runId());
        assertThat(adapter.activeRuns()).doesNotContainKey(second.runId());
        assertThat(adapter.activeRuns()).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private void insertActiveRun(AgentScopeRuntimeAdapter adapter, String runId, String userId, Long sessionId) {
        try {
            RuntimeContext context = adapter.createContext(userId, sessionId);
            AgentScopeRuntimeAdapter.ActiveRun activeRun = new AgentScopeRuntimeAdapter.ActiveRun(
                    userId,
                    sessionId,
                    context,
                    adapter.workspaceRoot(userId, sessionId),
                    Instant.now());
            Field activeRuns = AgentScopeRuntimeAdapter.class.getDeclaredField("activeRuns");
            activeRuns.setAccessible(true);
            ((ConcurrentMap<String, AgentScopeRuntimeAdapter.ActiveRun>) activeRuns.get(adapter)).put(runId, activeRun);
            Field activeSessionRunIds = AgentScopeRuntimeAdapter.class.getDeclaredField("activeSessionRunIds");
            activeSessionRunIds.setAccessible(true);
            ((ConcurrentMap<Long, String>) activeSessionRunIds.get(adapter)).put(sessionId, runId);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    private static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private static void sendSse(HttpExchange exchange, String body) throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private static java.util.List<String> toolNames(JsonNode request) {
        java.util.List<String> names = new java.util.ArrayList<>();
        request.path("tools").forEach(tool -> names.add(tool.path("function").path("name").asText()));
        return names;
    }

}
