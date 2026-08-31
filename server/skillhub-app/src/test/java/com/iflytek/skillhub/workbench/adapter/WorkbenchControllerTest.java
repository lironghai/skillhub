package com.iflytek.skillhub.workbench.adapter;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import com.iflytek.skillhub.mcp.McpAssociatedItemResponse;
import com.iflytek.skillhub.mcp.McpCatalogService;
import com.iflytek.skillhub.mcp.McpInternalServerItemResponse;
import com.iflytek.skillhub.mcp.McpInternalServerResponse;
import com.iflytek.skillhub.workbench.application.WorkbenchPackageExcludedFile;
import com.iflytek.skillhub.workbench.application.WorkbenchPackageFile;
import com.iflytek.skillhub.workbench.application.WorkbenchPackagePreviewResult;
import com.iflytek.skillhub.workbench.application.WorkbenchPackagePublishCommand;
import com.iflytek.skillhub.workbench.application.WorkbenchPackagePublishResult;
import com.iflytek.skillhub.workbench.application.WorkbenchPackageValidationReport;
import com.iflytek.skillhub.workbench.application.WorkbenchFileContent;
import com.iflytek.skillhub.workbench.application.WorkbenchSessionApplicationService;
import com.iflytek.skillhub.workbench.domain.WorkbenchMcpBinding;
import com.iflytek.skillhub.workbench.domain.WorkbenchMcpCatalogSource;
import com.iflytek.skillhub.workbench.domain.WorkbenchSession;
import com.iflytek.skillhub.workbench.domain.WorkbenchToolApproval;
import com.iflytek.skillhub.workbench.domain.WorkbenchToolRiskLevel;
import com.iflytek.skillhub.workbench.port.AgentRuntimeRunHandle;
import com.iflytek.skillhub.workbench.port.AgentRuntimeRunStatus;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WorkbenchControllerTest {

    private WorkbenchSessionApplicationService workbenchService;
    private McpCatalogService mcpCatalogService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        workbenchService = mock(WorkbenchSessionApplicationService.class);
        mcpCatalogService = mock(McpCatalogService.class);
        WorkbenchController controller = new WorkbenchController(
                workbenchService,
                mock(NamespaceRepository.class),
                mock(SkillRepository.class),
                mock(SkillVersionRepository.class),
                mcpCatalogService,
                responseFactory(),
                Clock.fixed(Instant.parse("2026-07-31T00:00:00Z"), ZoneOffset.UTC),
                false,
                "",
                "",
                "",
                "");
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void runtimeConfigReportsMissingModelExecutorWithoutSecrets() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(auth());

        mockMvc.perform(get("/api/web/workbench/runtime-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.modelExecutorEnabled").value(false))
                .andExpect(jsonPath("$.data.modelConfigured").value(false))
                .andExpect(jsonPath("$.data.modelBaseUrlConfigured").value(false))
                .andExpect(jsonPath("$.data.modelApiKeyConfigured").value(false))
                .andExpect(jsonPath("$.data.displayStatus").value("MODEL_NOT_CONFIGURED"))
                .andExpect(jsonPath("$.data.message").value("模型执行器未配置；当前只创建 AgentScope 会话上下文，不会调用真实模型。"));
    }

    @Test
    void readFileUsesQueryParameterPathAndPrincipal() throws Exception {
        when(workbenchService.readFile(eq(7L), anyString(), eq("references/guide.md")))
                .thenReturn(new WorkbenchFileContent(
                        "references/guide.md",
                        "hello".getBytes(StandardCharsets.UTF_8),
                        "text/markdown"));

        SecurityContextHolder.getContext().setAuthentication(auth());

        mockMvc.perform(get("/api/web/workbench/sessions/7/file")
                        .param("path", "references/guide.md"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.path").value("references/guide.md"))
                .andExpect(jsonPath("$.data.content").value("hello"));

        verify(workbenchService).readFile(eq(7L), eq("user-1"), eq("references/guide.md"));
    }

    @Test
    void writeFileAllowsEmptyContent() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(auth());

        mockMvc.perform(put("/api/web/workbench/sessions/7/file")
                        .param("path", "SKILL.md")
                        .contentType("application/json")
                        .content("""
                                {"content":"","contentType":"text/markdown"}
                                """)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sizeBytes").value(0));

        verify(workbenchService).writeFile(
                eq(7L),
                eq("user-1"),
                eq("SKILL.md"),
                aryEq(new byte[0]),
                eq("text/markdown"));
    }

    @Test
    void listSessionsUsesPrincipalLimitAndReturnsSessionSummaries() throws Exception {
        WorkbenchSession session = WorkbenchSession.createSkill(
                "user-1",
                10L,
                "draft-skill",
                "0.1.0",
                "workspace-18",
                Instant.parse("2026-08-01T00:00:00Z"),
                Clock.fixed(Instant.parse("2026-07-31T00:00:00Z"), ZoneOffset.UTC));
        setField(session, "id", 18L);
        when(workbenchService.listSessions(eq("user-1"), eq(5))).thenReturn(List.of(session));

        SecurityContextHolder.getContext().setAuthentication(auth());

        mockMvc.perform(get("/api/web/workbench/sessions")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(18))
                .andExpect(jsonPath("$.data[0].mode").value("CREATE_SKILL"))
                .andExpect(jsonPath("$.data[0].status").value("DRAFT"))
                .andExpect(jsonPath("$.data[0].targetSlug").value("draft-skill"))
                .andExpect(jsonPath("$.data[0].targetVersion").value("0.1.0"))
                .andExpect(jsonPath("$.data[0].fileCount").value(0));

        verify(workbenchService).listSessions(eq("user-1"), eq(5));
    }

    @Test
    void listSessionsIncludesActiveRunSummaryWhenRuntimeIsStillRunning() throws Exception {
        WorkbenchSession session = WorkbenchSession.createSkill(
                "user-1",
                10L,
                "running-skill",
                "0.1.0",
                "workspace-19",
                Instant.parse("2026-08-01T00:00:00Z"),
                Clock.fixed(Instant.parse("2026-07-31T00:00:00Z"), ZoneOffset.UTC));
        setField(session, "id", 19L);
        when(workbenchService.listSessions(eq("user-1"), eq(5))).thenReturn(List.of(session));
        when(workbenchService.findActiveRun(eq(session), eq("user-1")))
                .thenReturn(Optional.of(new AgentRuntimeRunHandle(
                        "run-active",
                        AgentRuntimeRunStatus.STARTED,
                        "running token=secret")));

        SecurityContextHolder.getContext().setAuthentication(auth());

        mockMvc.perform(get("/api/web/workbench/sessions")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].activeRun.runId").value("run-active"))
                .andExpect(jsonPath("$.data[0].activeRun.status").value("STARTED"))
                .andExpect(jsonPath("$.data[0].activeRun.message").value("running token=[REDACTED]"));
    }

    @Test
    void sendMessageUsesPrincipalAndReturnsRuntimeHandle() throws Exception {
        when(workbenchService.sendMessage(eq(7L), anyString(), eq("创建技能")))
                .thenReturn(new AgentRuntimeRunHandle(
                        "run-1",
                        AgentRuntimeRunStatus.STARTED,
                        "started token=secret https://internal.example/runtime"));

        SecurityContextHolder.getContext().setAuthentication(auth());

        mockMvc.perform(post("/api/web/workbench/sessions/7/messages")
                        .contentType("application/json")
                        .content("""
                                {"message":"创建技能"}
                                """)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.runId").value("run-1"))
                .andExpect(jsonPath("$.data.status").value("STARTED"))
                .andExpect(jsonPath("$.data.message").value("started token=[REDACTED] [REDACTED_URL]"));

        verify(workbenchService).sendMessage(eq(7L), eq("user-1"), eq("创建技能"));
    }

    @Test
    void cancelRunUsesPrincipalAndReturnsRuntimeHandle() throws Exception {
        when(workbenchService.cancelRun(eq(7L), anyString(), eq("run-1")))
                .thenReturn(new AgentRuntimeRunHandle(
                        "run-1",
                        AgentRuntimeRunStatus.CANCELLED,
                        "cancelled Authorization: Bearer runtime-secret"));

        SecurityContextHolder.getContext().setAuthentication(auth());

        mockMvc.perform(post("/api/web/workbench/sessions/7/runs/run-1/cancel")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.runId").value("run-1"))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.message").value("cancelled Authorization=[REDACTED]"));

        verify(workbenchService).cancelRun(eq(7L), eq("user-1"), eq("run-1"));
    }

    @Test
    void mcpCatalogUsesPrincipalAndDoesNotExposeRuntimeEndpointUrls() throws Exception {
        when(workbenchService.getSession(eq(7L), anyString()))
                .thenReturn(WorkbenchSession.createSkill(
                        "user-1",
                        10L,
                        "skill",
                        "1.0.0",
                        "workspace",
                        Instant.parse("2026-08-01T00:00:00Z"),
                        Clock.fixed(Instant.parse("2026-07-31T00:00:00Z"), ZoneOffset.UTC)));
        when(mcpCatalogService.internalServers(eq("server"), eq("0"), eq("20")))
                .thenReturn(new McpInternalServerResponse(
                        List.of(new McpInternalServerItemResponse(
                                "server-a",
                                "Server A",
                                "Allowed metadata",
                                true,
                                "PRIVATE",
                                "owner@example.com",
                                "team-a",
                                1,
                                0,
                                0,
                                List.of(new McpAssociatedItemResponse("tool-a", "Tool A", "Reads data")),
                                List.of(),
                                List.of(),
                                List.of("safe"),
                                "https://internal.example/mcp",
                                "https://internal.example/sse")),
                        1,
                        0,
                        20));

        SecurityContextHolder.getContext().setAuthentication(auth());

        mockMvc.perform(get("/api/web/workbench/sessions/7/mcp-catalog")
                        .param("search", "server"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value("server-a"))
                .andExpect(jsonPath("$.data.items[0].catalogSource").value("CONTEXT_FORGE"))
                .andExpect(jsonPath("$.data.items[0].runtimeCandidate").value(true))
                .andExpect(jsonPath("$.data.items[0].streamableHttpUrl").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].sseUrl").doesNotExist());

        verify(workbenchService).getSession(eq(7L), eq("user-1"));
    }

    @Test
    void mcpBindingsUsePrincipalForReadAndUpdate() throws Exception {
        when(workbenchService.updateMcpBindings(eq(7L), anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(new WorkbenchMcpBinding(
                        7L,
                        "server-a",
                        WorkbenchMcpCatalogSource.CONTEXT_FORGE,
                        "context-forge:server-a",
                        null,
                        null,
                        null,
                        "v1",
                        Clock.fixed(Instant.parse("2026-07-31T00:00:00Z"), ZoneOffset.UTC))));
        when(workbenchService.listMcpBindings(eq(7L), anyString()))
                .thenReturn(List.of(new WorkbenchMcpBinding(
                        7L,
                        "server-a",
                        WorkbenchMcpCatalogSource.CONTEXT_FORGE,
                        "context-forge:server-a",
                        null,
                        null,
                        null,
                        "v1",
                        Clock.fixed(Instant.parse("2026-07-31T00:00:00Z"), ZoneOffset.UTC))));

        SecurityContextHolder.getContext().setAuthentication(auth());

        mockMvc.perform(post("/api/web/workbench/sessions/7/mcp-bindings")
                        .contentType("application/json")
                        .content("""
                                {"serverIds":["server-a","server-a"]}
                                """)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].serverId").value("server-a"))
                .andExpect(jsonPath("$.data[0].runtimeEndpointRef").doesNotExist());

        verify(workbenchService).updateMcpBindings(eq(7L), eq("user-1"), org.mockito.ArgumentMatchers.any());

        mockMvc.perform(get("/api/web/workbench/sessions/7/mcp-bindings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].serverId").value("server-a"));

        verify(workbenchService).listMcpBindings(eq(7L), eq("user-1"));
    }

    @Test
    void approvalsUsePrincipalAndReturnDecision() throws Exception {
        when(workbenchService.listPendingApprovals(eq(7L), anyString()))
                .thenReturn(List.of(new WorkbenchToolApproval(
                        7L,
                        100L,
                        "deploy",
                        "server-a",
                        WorkbenchToolRiskLevel.MUTATING,
                        "{}",
                        Clock.fixed(Instant.parse("2026-07-31T00:00:00Z"), ZoneOffset.UTC))));
        when(workbenchService.approveToolApproval(eq(7L), eq(11L), anyString()))
                .thenReturn(new WorkbenchToolApproval(
                        7L,
                        100L,
                        "deploy",
                        "server-a",
                        WorkbenchToolRiskLevel.MUTATING,
                        "{}",
                        Clock.fixed(Instant.parse("2026-07-31T00:00:00Z"), ZoneOffset.UTC)));

        SecurityContextHolder.getContext().setAuthentication(auth());

        mockMvc.perform(get("/api/web/workbench/sessions/7/approvals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].toolName").value("deploy"));

        verify(workbenchService).listPendingApprovals(eq(7L), eq("user-1"));

        mockMvc.perform(post("/api/web/workbench/sessions/7/approvals/11/approve")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.toolName").value("deploy"));

        verify(workbenchService).approveToolApproval(eq(7L), eq(11L), eq("user-1"));
    }

    @Test
    void packagePreviewUsesPrincipalVisibilityAndReturnsPreviewContract() throws Exception {
        when(workbenchService.previewPackage(eq(7L), anyString(), eq(SkillVisibility.PUBLIC), eq(Set.of("USER"))))
                .thenReturn(new WorkbenchPackagePreviewResult(
                        "sha256:abc",
                        true,
                        List.of(new WorkbenchPackageFile("SKILL.md", 128L, "file-sha")),
                        List.of(new WorkbenchPackageExcludedFile("AGENTS.md", "WORKBENCH_RUNTIME_ARTIFACT")),
                        new WorkbenchPackageValidationReport("PASS", List.of(), List.of(), List.of(), "demo", "1.0.0")));

        SecurityContextHolder.getContext().setAuthentication(auth());

        mockMvc.perform(post("/api/web/workbench/sessions/7/package-preview")
                        .contentType("application/json")
                        .content("""
                                {"visibility":"PUBLIC"}
                                """)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.packageFingerprint").value("sha256:abc"))
                .andExpect(jsonPath("$.data.readyToPublish").value(true))
                .andExpect(jsonPath("$.data.includedFiles[0].path").value("SKILL.md"))
                .andExpect(jsonPath("$.data.excludedFiles[0].path").value("AGENTS.md"))
                .andExpect(jsonPath("$.data.validation.status").value("PASS"));

        verify(workbenchService).previewPackage(eq(7L), eq("user-1"), eq(SkillVisibility.PUBLIC), eq(Set.of("USER")));
    }

    @Test
    void publishUsesConfirmedFingerprintVisibilityAndPrincipal() throws Exception {
        when(workbenchService.publishPackage(eq(7L), anyString(), any(WorkbenchPackagePublishCommand.class)))
                .thenReturn(new WorkbenchPackagePublishResult(
                        456L,
                        789L,
                        "team-a",
                        "report-helper",
                        "1.3.0",
                        "PENDING_REVIEW"));

        SecurityContextHolder.getContext().setAuthentication(auth());

        mockMvc.perform(post("/api/web/workbench/sessions/7/publish")
                        .contentType("application/json")
                        .content("""
                                {"confirmPackageFingerprint":"sha256:abc","visibility":"PUBLIC"}
                                """)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skillId").value(456))
                .andExpect(jsonPath("$.data.skillVersionId").value(789))
                .andExpect(jsonPath("$.data.namespace").value("team-a"))
                .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"));

        verify(workbenchService).publishPackage(
                eq(7L),
                eq("user-1"),
                eq(new WorkbenchPackagePublishCommand("sha256:abc", SkillVisibility.PUBLIC, Set.of("USER"))));
    }

    private UsernamePasswordAuthenticationToken auth() {
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-1",
                "User One",
                "user@example.com",
                "",
                "local",
                Set.of("USER"));
        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private ApiResponseFactory responseFactory() {
        StaticMessageSource messages = new StaticMessageSource();
        messages.addMessage("response.success.read", Locale.CHINA, "ok");
        messages.addMessage("response.success.created", Locale.CHINA, "ok");
        messages.addMessage("response.success.updated", Locale.CHINA, "ok");
        messages.addMessage("response.success.published", Locale.CHINA, "ok");
        return new ApiResponseFactory(
                messages,
                Clock.fixed(Instant.parse("2026-07-31T00:00:00Z"), ZoneOffset.UTC),
                new RequestIdAccessor());
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
}
