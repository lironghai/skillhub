package com.iflytek.skillhub.workbench.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.SkillhubApplication;
import com.iflytek.skillhub.TestRedisConfig;
import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.review.ReviewTask;
import com.iflytek.skillhub.domain.review.ReviewTaskRepository;
import com.iflytek.skillhub.domain.review.ReviewTaskStatus;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillFile;
import com.iflytek.skillhub.domain.skill.SkillFileRepository;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.infra.jpa.NamespaceJpaRepository;
import com.iflytek.skillhub.infra.jpa.UserAccountJpaRepository;
import com.iflytek.skillhub.search.SearchEmbeddingService;
import com.iflytek.skillhub.storage.ObjectStorageService;
import com.iflytek.skillhub.mcp.McpAssociatedItemResponse;
import com.iflytek.skillhub.mcp.McpCatalogService;
import com.iflytek.skillhub.mcp.McpInternalServerItemResponse;
import com.iflytek.skillhub.mcp.McpInternalServerResponse;
import com.iflytek.skillhub.workbench.port.AgentRuntimeCancelCommand;
import com.iflytek.skillhub.workbench.port.AgentRuntimeEvent;
import com.iflytek.skillhub.workbench.port.AgentRuntimeEventType;
import com.iflytek.skillhub.workbench.port.AgentRuntimePort;
import com.iflytek.skillhub.workbench.port.AgentRuntimeRunCommand;
import com.iflytek.skillhub.workbench.port.AgentRuntimeRunHandle;
import com.iflytek.skillhub.workbench.port.AgentRuntimeRunStatus;
import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = SkillhubApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class WorkbenchFlowIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path STORAGE_DIR = Path.of(
            System.getProperty("java.io.tmpdir"),
            "skillhub-workbench-it-" + UUID.randomUUID());

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountJpaRepository userAccountRepository;

    @Autowired
    private NamespaceJpaRepository namespaceRepository;

    @Autowired
    private NamespaceMemberRepository namespaceMemberRepository;

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private SkillVersionRepository skillVersionRepository;

    @Autowired
    private SkillFileRepository skillFileRepository;

    @Autowired
    private ReviewTaskRepository reviewTaskRepository;

    @Autowired
    private ObjectStorageService objectStorageService;

    @MockBean
    private DeviceAuthService deviceAuthService;

    @MockBean
    private RbacService rbacService;

    @MockBean
    private SearchEmbeddingService searchEmbeddingService;

    @MockBean
    private McpCatalogService mcpCatalogService;

    @MockBean
    private AgentRuntimePort runtimePort;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("skillhub.storage.provider", () -> "local");
        registry.add("skillhub.storage.local.base-path", STORAGE_DIR::toString);
        registry.add("skillhub.workbench.runtime.workspace-base-path",
                () -> STORAGE_DIR.resolve("runtime").toString());
    }

    @BeforeEach
    void setUp() {
        when(searchEmbeddingService.embed(anyString())).thenReturn("");
        when(searchEmbeddingService.similarity(anyString(), anyString())).thenReturn(0.0d);
        when(rbacService.getUserRoleCodes(anyString())).thenReturn(Set.of("USER"));
        when(runtimePort.start(any(AgentRuntimeRunCommand.class))).thenReturn(new AgentRuntimeRunHandle(
                "run-default",
                AgentRuntimeRunStatus.STARTED,
                "started",
                List.of(AgentRuntimeEvent.modelMessage("runtime ready"))));
        when(runtimePort.cancel(any(AgentRuntimeCancelCommand.class))).thenReturn(new AgentRuntimeRunHandle(
                "run-default",
                AgentRuntimeRunStatus.CANCELLED,
                "cancelled",
                List.of(AgentRuntimeEvent.modelMessage("cancelled"))));
        when(mcpCatalogService.internalServers(anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    String search = invocation.getArgument(0, String.class);
                    if (!"server-a".equals(search)) {
                        return new McpInternalServerResponse(List.of(), 0, 0, 50);
                    }
                    return new McpInternalServerResponse(
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
                            50);
                });
    }

    @Test
    void updateSessionImportsEditsDiffsAndRejectsNonOwner() throws Exception {
        SourceSkillGraph graph = createSourceSkillGraph("workbench-user");

        String createResponse = mockMvc.perform(post("/api/web/workbench/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "namespace": "%s",
                                  "mode": "UPDATE_SKILL",
                                  "sourceSkill": {
                                    "namespace": "%s",
                                    "slug": "%s",
                                    "version": "1.0.0"
                                  },
                                  "targetVersion": "1.1.0"
                                }
                                """.formatted(graph.namespace().getSlug(), graph.namespace().getSlug(), graph.skill().getSlug()))
                        .with(authentication(auth("workbench-user")))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.mode").value("UPDATE_SKILL"))
                .andExpect(jsonPath("$.data.targetVersion").value("1.1.0"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Number sessionIdNumber = JsonPath.read(createResponse, "$.data.id");
        long sessionId = sessionIdNumber.longValue();

        mockMvc.perform(post("/api/web/workbench/sessions/" + sessionId + "/import-source")
                        .with(authentication(auth("workbench-user")))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.importedFiles[0]").value("SKILL.md"));

        mockMvc.perform(get("/api/web/workbench/sessions/" + sessionId + "/file")
                        .param("path", "SKILL.md")
                        .with(authentication(auth("workbench-user"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("# Original skill"));

        String runResponse = mockMvc.perform(post("/api/web/workbench/sessions/" + sessionId + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"请分析并准备更新这个技能"}
                                """)
                        .with(authentication(auth("workbench-user")))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("STARTED"))
                .andExpect(jsonPath("$.data.eventCount").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String runId = JsonPath.read(runResponse, "$.data.runId");

        mockMvc.perform(get("/api/web/workbench/sessions/" + sessionId + "/events")
                        .with(authentication(auth("workbench-user"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.type=='USER_MESSAGE')]").isNotEmpty())
                .andExpect(jsonPath("$.data[?(@.type=='MODEL_MESSAGE')]").isNotEmpty());

        mockMvc.perform(put("/api/web/workbench/sessions/" + sessionId + "/file")
                        .param("path", "SKILL.md")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"# Updated skill","contentType":"text/markdown"}
                                """)
                        .with(authentication(auth("workbench-user")))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sizeBytes").value(15));

        mockMvc.perform(get("/api/web/workbench/sessions/" + sessionId + "/diff")
                        .with(authentication(auth("workbench-user"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.files[0].path").value("SKILL.md"))
                .andExpect(jsonPath("$.data.files[0].status").value("MODIFIED"));

        mockMvc.perform(post("/api/web/workbench/sessions/" + sessionId + "/runs/" + runId + "/cancel")
                        .with(authentication(auth("workbench-user")))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        mockMvc.perform(get("/api/web/workbench/sessions/" + sessionId + "/file")
                        .param("path", "SKILL.md")
                        .with(authentication(auth("other-user"))))
                .andExpect(status().isForbidden());

        assertThat(skillVersionRepository.findById(graph.version().getId()))
                .get()
                .extracting(SkillVersion::getVersion)
                .isEqualTo("1.0.0");
        assertThat(new String(objectStorageService.getObject(graph.storageKey()).readAllBytes(), StandardCharsets.UTF_8))
                .isEqualTo("# Original skill");
    }

    @Test
    void mcpBindingAndApprovalApproveFlowPersistsBackendState() throws Exception {
        SourceSkillGraph graph = createSourceSkillGraph("approval-user");
        long sessionId = createWorkbenchSession(graph, "approval-user");

        mockMvc.perform(post("/api/web/workbench/sessions/" + sessionId + "/mcp-bindings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"serverIds":["server-a","server-a"]}
                                """)
                        .with(authentication(auth("approval-user")))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].serverId").value("server-a"))
                .andExpect(jsonPath("$.data[0].runtimeEndpointRef").doesNotExist());

        when(runtimePort.start(any(AgentRuntimeRunCommand.class))).thenReturn(new AgentRuntimeRunHandle(
                "run-approval",
                AgentRuntimeRunStatus.STARTED,
                "approval required",
                List.of(new AgentRuntimeEvent(
                        AgentRuntimeEventType.APPROVAL_REQUIRED,
                        "{\"toolName\":\"deploy\",\"mcpServerId\":\"server-a\",\"risk\":\"MUTATING\",\"arguments\":{\"apiToken\":\"secret\"}}",
                        null,
                        null,
                        null))));

        mockMvc.perform(post("/api/web/workbench/sessions/" + sessionId + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"call deploy"}
                                """)
                        .with(authentication(auth("approval-user")))
                        .with(csrf()))
                .andExpect(status().isOk());

        String approvalsResponse = mockMvc.perform(get("/api/web/workbench/sessions/" + sessionId + "/approvals")
                        .with(authentication(auth("approval-user"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].toolName").value("deploy"))
                .andExpect(jsonPath("$.data[0].argumentsRedactedJson.apiToken").value("[REDACTED]"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String eventsResponse = mockMvc.perform(get("/api/web/workbench/sessions/" + sessionId + "/events")
                        .with(authentication(auth("approval-user"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.type == 'APPROVAL_REQUIRED')].payloadJson").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(eventsResponse).doesNotContain("secret", "apiToken", "internal.example");

        Number approvalIdNumber = JsonPath.read(approvalsResponse, "$.data[0].id");
        long approvalId = approvalIdNumber.longValue();

        mockMvc.perform(post("/api/web/workbench/sessions/" + sessionId + "/approvals/" + approvalId + "/approve")
                        .with(authentication(auth("approval-user")))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    void packagePreviewAndPublishCreatesNewSkillVersionWithoutChangingSourceVersion() throws Exception {
        SourceSkillGraph graph = createSourceSkillGraph("publish-user");
        long sessionId = createWorkbenchSession(graph, "publish-user");

        mockMvc.perform(post("/api/web/workbench/sessions/" + sessionId + "/import-source")
                        .with(authentication(auth("publish-user")))
                        .with(csrf()))
                .andExpect(status().isOk());

        String updatedSkillMd = """
                ---
                name: %s
                description: Workbench publish integration skill.
                version: 1.1.0
                ---

                # Updated skill
                """.formatted(graph.skill().getSlug());
        mockMvc.perform(put("/api/web/workbench/sessions/" + sessionId + "/file")
                        .param("path", "SKILL.md")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":%s,"contentType":"text/markdown"}
                                """.formatted(JSON.writeValueAsString(updatedSkillMd)))
                        .with(authentication(auth("publish-user")))
                        .with(csrf()))
                .andExpect(status().isOk());

        String previewResponse = mockMvc.perform(post("/api/web/workbench/sessions/" + sessionId + "/package-preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"visibility":"PRIVATE"}
                                """)
                        .with(authentication(auth("publish-user")))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.readyToPublish").value(true))
                .andExpect(jsonPath("$.data.validation.status").value("PASS"))
                .andExpect(jsonPath("$.data.validation.resolvedSlug").value(graph.skill().getSlug()))
                .andExpect(jsonPath("$.data.validation.resolvedVersion").value("1.1.0"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String fingerprint = JsonPath.read(previewResponse, "$.data.packageFingerprint");

        mockMvc.perform(post("/api/web/workbench/sessions/" + sessionId + "/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"confirmPackageFingerprint":%s,"visibility":"PRIVATE"}
                                """.formatted(JSON.writeValueAsString(fingerprint)))
                        .with(authentication(auth("publish-user")))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skillId").value(graph.skill().getId()))
                .andExpect(jsonPath("$.data.slug").value(graph.skill().getSlug()))
                .andExpect(jsonPath("$.data.version").value("1.1.0"));

        assertThat(skillVersionRepository.findById(graph.version().getId()))
                .get()
                .extracting(SkillVersion::getVersion)
                .isEqualTo("1.0.0");
        SkillVersion newVersion = skillVersionRepository
                .findBySkillIdAndVersion(graph.skill().getId(), "1.1.0")
                .orElseThrow();
        assertThat(newVersion.getStatus()).isEqualTo(SkillVersionStatus.UPLOADED);
        assertThat(skillFileRepository.findByVersionId(newVersion.getId()))
                .hasSize(1)
                .first()
                .satisfies(file -> {
                    assertThat(file.getFilePath()).isEqualTo("SKILL.md");
                    assertThat(readObject(file.getStorageKey())).contains("version: 1.1.0");
                });

        byte[] downloadedPackage = mockMvc.perform(get("/api/v1/skills/%s/%s/versions/1.1.0/download"
                                .formatted(graph.namespace().getSlug(), graph.skill().getSlug()))
                        .with(authentication(auth("publish-user"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        assertThat(zipEntries(downloadedPackage)).containsExactly("SKILL.md");
    }

    @Test
    void workbenchPublishRejectsExistingTargetVersionWithoutReplacingIt() throws Exception {
        SourceSkillGraph graph = createSourceSkillGraph("existing-target-user");
        SkillVersion existingTarget = new SkillVersion(graph.skill().getId(), "1.1.0", "existing-target-user");
        existingTarget.setStatus(SkillVersionStatus.UPLOADED);
        existingTarget.setRequestedVisibility(SkillVisibility.PRIVATE);
        existingTarget = skillVersionRepository.save(existingTarget);
        skillFileRepository.save(new SkillFile(
                existingTarget.getId(),
                "SKILL.md",
                18L,
                "text/markdown",
                "1".repeat(64),
                graph.storageKey()));

        long sessionId = createWorkbenchSession(graph, "existing-target-user");
        importSourceAndWriteSkillMd(sessionId, "existing-target-user", graph.skill().getSlug(), "1.1.0");
        String fingerprint = previewPrivatePackage(sessionId, "existing-target-user");

        mockMvc.perform(post("/api/web/workbench/sessions/" + sessionId + "/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"confirmPackageFingerprint":%s,"visibility":"PRIVATE"}
                                """.formatted(JSON.writeValueAsString(fingerprint)))
                        .with(authentication(auth("existing-target-user")))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("Workbench publish requires a new version. Version already exists: 1.1.0"));

        assertThat(skillVersionRepository.findById(existingTarget.getId()))
                .get()
                .extracting(SkillVersion::getStatus)
                .isEqualTo(SkillVersionStatus.UPLOADED);
        assertThat(skillVersionRepository.findBySkillIdAndVersion(graph.skill().getId(), "1.1.0"))
                .get()
                .extracting(SkillVersion::getId)
                .isEqualTo(existingTarget.getId());
    }

    @Test
    void workbenchPublishRejectsTargetVersionCreatedAfterPreviewWithoutReplacingIt() throws Exception {
        SourceSkillGraph graph = createSourceSkillGraph("race-target-user");
        long sessionId = createWorkbenchSession(graph, "race-target-user");
        importSourceAndWriteSkillMd(sessionId, "race-target-user", graph.skill().getSlug(), "1.1.0");
        String fingerprint = previewPrivatePackage(sessionId, "race-target-user");

        SkillVersion concurrentVersion = new SkillVersion(graph.skill().getId(), "1.1.0", "race-target-user");
        concurrentVersion.setStatus(SkillVersionStatus.UPLOADED);
        skillVersionRepository.save(concurrentVersion);

        mockMvc.perform(post("/api/web/workbench/sessions/" + sessionId + "/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"confirmPackageFingerprint":%s,"visibility":"PRIVATE"}
                                """.formatted(JSON.writeValueAsString(fingerprint)))
                        .with(authentication(auth("race-target-user")))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value("Workbench publish requires a new version. Version already exists: 1.1.0"));

        assertThat(skillVersionRepository.findById(concurrentVersion.getId()))
                .get()
                .extracting(SkillVersion::getStatus)
                .isEqualTo(SkillVersionStatus.UPLOADED);
    }

    @Test
    void workbenchPublishRejectsPendingReviewCreatedAfterPreviewWithoutWithdrawingIt() throws Exception {
        SourceSkillGraph graph = createSourceSkillGraph("race-pending-user");
        long sessionId = createWorkbenchSession(graph, "race-pending-user");
        importSourceAndWriteSkillMd(sessionId, "race-pending-user", graph.skill().getSlug(), "1.1.0");
        String fingerprint = previewPrivatePackage(sessionId, "race-pending-user");

        SkillVersion pendingVersion = new SkillVersion(graph.skill().getId(), "2.0.0", "race-pending-user");
        pendingVersion.setStatus(SkillVersionStatus.PENDING_REVIEW);
        pendingVersion = skillVersionRepository.save(pendingVersion);
        ReviewTask pendingTask = reviewTaskRepository.save(new ReviewTask(
                pendingVersion.getId(),
                graph.skill().getNamespaceId(),
                "race-pending-user"));

        mockMvc.perform(post("/api/web/workbench/sessions/" + sessionId + "/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"confirmPackageFingerprint":%s,"visibility":"PRIVATE"}
                                """.formatted(JSON.writeValueAsString(fingerprint)))
                        .with(authentication(auth("race-pending-user")))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value("Workbench publish is blocked because skill '"
                        + graph.skill().getSlug() + "' has a pending review version"));

        assertThat(skillVersionRepository.findById(pendingVersion.getId()))
                .get()
                .extracting(SkillVersion::getStatus)
                .isEqualTo(SkillVersionStatus.PENDING_REVIEW);
        assertThat(reviewTaskRepository.findById(pendingTask.getId()))
                .get()
                .extracting(ReviewTask::getStatus)
                .isEqualTo(ReviewTaskStatus.PENDING);
    }

    @Test
    void workbenchPublishRejectsWhenSkillHasPendingReviewWithoutWithdrawingIt() throws Exception {
        SourceSkillGraph graph = createSourceSkillGraph("pending-review-user");
        SkillVersion pendingVersion = new SkillVersion(graph.skill().getId(), "1.0.1", "pending-review-user");
        pendingVersion.setStatus(SkillVersionStatus.PENDING_REVIEW);
        pendingVersion.setRequestedVisibility(SkillVisibility.PUBLIC);
        pendingVersion = skillVersionRepository.save(pendingVersion);
        ReviewTask pendingReview = reviewTaskRepository.save(new ReviewTask(
                pendingVersion.getId(),
                graph.namespace().getId(),
                "pending-review-user"));

        long sessionId = createWorkbenchSession(graph, "pending-review-user");
        importSourceAndWriteSkillMd(sessionId, "pending-review-user", graph.skill().getSlug(), "1.1.0");
        String fingerprint = previewPrivatePackage(sessionId, "pending-review-user");

        mockMvc.perform(post("/api/web/workbench/sessions/" + sessionId + "/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"confirmPackageFingerprint":%s,"visibility":"PRIVATE"}
                                """.formatted(JSON.writeValueAsString(fingerprint)))
                        .with(authentication(auth("pending-review-user")))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        assertThat(skillVersionRepository.findById(pendingVersion.getId()))
                .get()
                .extracting(SkillVersion::getStatus)
                .isEqualTo(SkillVersionStatus.PENDING_REVIEW);
        assertThat(reviewTaskRepository.findBySkillVersionIdAndStatus(pendingVersion.getId(), ReviewTaskStatus.PENDING))
                .get()
                .extracting(ReviewTask::getId)
                .isEqualTo(pendingReview.getId());
    }

    private void importSourceAndWriteSkillMd(long sessionId, String userId, String skillSlug, String version) throws Exception {
        mockMvc.perform(post("/api/web/workbench/sessions/" + sessionId + "/import-source")
                        .with(authentication(auth(userId)))
                        .with(csrf()))
                .andExpect(status().isOk());

        String updatedSkillMd = """
                ---
                name: %s
                description: Workbench publish integration skill.
                version: %s
                ---

                # Updated skill
                """.formatted(skillSlug, version);
        mockMvc.perform(put("/api/web/workbench/sessions/" + sessionId + "/file")
                        .param("path", "SKILL.md")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":%s,"contentType":"text/markdown"}
                                """.formatted(JSON.writeValueAsString(updatedSkillMd)))
                        .with(authentication(auth(userId)))
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    private String previewPrivatePackage(long sessionId, String userId) throws Exception {
        String previewResponse = mockMvc.perform(post("/api/web/workbench/sessions/" + sessionId + "/package-preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"visibility":"PRIVATE"}
                                """)
                        .with(authentication(auth(userId)))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.readyToPublish").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(previewResponse, "$.data.packageFingerprint");
    }

    private List<String> zipEntries(byte[] packageBytes) {
        List<String> entries = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(packageBytes))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                entries.add(entry.getName());
                zip.closeEntry();
            }
        } catch (Exception ex) {
            throw new AssertionError("failed to inspect downloaded package zip", ex);
        }
        return entries;
    }

    private SourceSkillGraph createSourceSkillGraph(String userId) {
        userAccountRepository.saveAndFlush(new UserAccount(userId, userId + "@example.com", userId, "local"));

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Namespace namespace = namespaceRepository.saveAndFlush(new Namespace(
                "workbench-" + suffix,
                "Workbench " + suffix,
                userId));
        namespaceMemberRepository.save(new NamespaceMember(namespace.getId(), userId, NamespaceRole.ADMIN));

        Skill skill = new Skill(namespace.getId(), "source-" + suffix, userId, SkillVisibility.PUBLIC);
        skill.setDisplayName("Source " + suffix);
        skill.setSummary("Source skill for workbench integration.");
        skill.setCreatedBy(userId);
        skill.setUpdatedBy(userId);
        skill = skillRepository.save(skill);

        SkillVersion version = new SkillVersion(skill.getId(), "1.0.0", userId);
        version.setStatus(SkillVersionStatus.PUBLISHED);
        version.setRequestedVisibility(SkillVisibility.PUBLIC);
        version.setFileCount(1);
        version.setTotalSize(16L);
        version.setDownloadReady(true);
        version = skillVersionRepository.save(version);
        skill.setLatestVersionId(version.getId());
        skillRepository.save(skill);

        String storageKey = "source/" + suffix + "/SKILL.md";
        byte[] content = "# Original skill".getBytes(StandardCharsets.UTF_8);
        objectStorageService.putObject(storageKey, new ByteArrayInputStream(content), content.length, "text/markdown");
        skillFileRepository.save(new SkillFile(
                version.getId(),
                "SKILL.md",
                (long) content.length,
                "text/markdown",
                "0".repeat(64),
                storageKey));

        return new SourceSkillGraph(namespace, skill, version, storageKey);
    }

    private long createWorkbenchSession(SourceSkillGraph graph, String userId) throws Exception {
        String createResponse = mockMvc.perform(post("/api/web/workbench/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "namespace": "%s",
                                  "mode": "UPDATE_SKILL",
                                  "sourceSkill": {
                                    "namespace": "%s",
                                    "slug": "%s",
                                    "version": "1.0.0"
                                  },
                                  "targetVersion": "1.1.0"
                                }
                                """.formatted(graph.namespace().getSlug(), graph.namespace().getSlug(), graph.skill().getSlug()))
                        .with(authentication(auth(userId)))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Number sessionIdNumber = JsonPath.read(createResponse, "$.data.id");
        return sessionIdNumber.longValue();
    }

    private String readObject(String storageKey) {
        try {
            return new String(objectStorageService.getObject(storageKey).readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new AssertionError("failed to read object: " + storageKey, ex);
        }
    }

    private UsernamePasswordAuthenticationToken auth(String userId) {
        PlatformPrincipal principal = new PlatformPrincipal(
                userId,
                userId,
                userId + "@example.com",
                "",
                "local",
                Set.of("USER"));
        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private record SourceSkillGraph(
            Namespace namespace,
            Skill skill,
            SkillVersion version,
            String storageKey) {
    }
}
