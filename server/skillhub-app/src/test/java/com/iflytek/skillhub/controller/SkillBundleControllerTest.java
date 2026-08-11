package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.SkillBundleDetailResponse;
import com.iflytek.skillhub.dto.SkillBundleSummaryResponse;
import com.iflytek.skillhub.service.SkillBundleAppService;
import com.iflytek.skillhub.service.SkillBundleDownloadService;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SkillBundleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SkillBundleAppService skillBundleAppService;

    @MockBean
    private SkillBundleDownloadService skillBundleDownloadService;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @Test
    void searchShouldReturnUnifiedEnvelopeAndParsePaging() throws Exception {
        when(skillBundleAppService.search(
                eq("agent"),
                eq("global"),
                eq("newest"),
                eq(1),
                eq(5),
                eq(null),
                eq("local-user"),
                ArgumentMatchers.<Map<Long, NamespaceRole>>any()))
                .thenReturn(new PageResponse<>(
                        List.of(new SkillBundleSummaryResponse(
                                7L,
                                "global",
                                "analysis-pack",
                                "Analysis Pack",
                                "Bundle summary",
                                "https://cdn.example.com/analysis.png",
                                "Analyst",
                                "Research",
                                "PUBLIC",
                                "PUBLISHED",
                                2,
                                3L,
                                List.of(),
                                Instant.parse("2026-07-28T00:00:00Z")
                        )),
                        1,
                        1,
                        5
                ));

        mockMvc.perform(get("/api/web/skill-bundles")
                        .param("q", "agent")
                        .param("namespace", "global")
                        .param("sort", "newest")
                        .param("page", "1")
                        .param("size", "5")
                        .requestAttr("userId", "local-user")
                        .requestAttr("userNsRoles", Map.of()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[0].slug").value("analysis-pack"))
                .andExpect(jsonPath("$.data.items[0].avatarUrl").value("https://cdn.example.com/analysis.png"))
                .andExpect(jsonPath("$.data.items[0].skillCount").value(2))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void detailShouldExposeBundleItems() throws Exception {
        when(skillBundleAppService.detail(eq("global"), eq("analysis-pack"), eq(null), any()))
                .thenReturn(new SkillBundleDetailResponse(
                        7L,
                        "global",
                        "analysis-pack",
                        "Analysis Pack",
                        "Summary",
                        "https://cdn.example.com/analysis.png",
                        "Description",
                        "Analyst",
                        "Research",
                        "Method",
                        "Use the skills in order",
                        "PUBLIC",
                        "PUBLISHED",
                        false,
                        3L,
                        List.of(),
                        List.of(),
                        Instant.parse("2026-07-28T00:00:00Z"),
                        Instant.parse("2026-07-28T00:00:00Z")
                ));

        mockMvc.perform(get("/api/v1/skill-bundles/global/analysis-pack"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatarUrl").value("https://cdn.example.com/analysis.png"))
                .andExpect(jsonPath("$.data.roleDescription").value("Analyst"))
                .andExpect(jsonPath("$.data.recommendedSkillNotes").value("Use the skills in order"));
    }

    @Test
    void downloadShouldReturnExpertPackageZipAttachment() throws Exception {
        byte[] zipBytes = "zip-content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(skillBundleDownloadService.downloadLatest(eq("global"), eq("superpowers"), eq(null), any()))
                .thenReturn(new SkillBundleDownloadService.DownloadResult(
                        () -> new ByteArrayInputStream(zipBytes),
                        "superpowers.zip",
                        zipBytes.length,
                        "application/zip"
                ));

        mockMvc.perform(get("/api/web/skill-bundles/global/superpowers/download"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"superpowers.zip\""))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType("application/zip"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().bytes(zipBytes));
    }

    @Test
    void createShouldRequireAuthenticatedUserAttribute() throws Exception {
        when(skillBundleAppService.create(any(), eq("local-user"), ArgumentMatchers.<Map<Long, NamespaceRole>>any()))
                .thenReturn(new SkillBundleDetailResponse(
                        8L,
                        "global",
                        "new-pack",
                        "New Pack",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "PUBLIC",
                        "DRAFT",
                        true,
                        0L,
                        List.of(),
                        List.of(),
                        Instant.parse("2026-07-28T00:00:00Z"),
                        Instant.parse("2026-07-28T00:00:00Z")
                ));

        mockMvc.perform(post("/api/web/skill-bundles")
                        .with(csrf())
                        .with(auth("local-user"))
                        .requestAttr("userId", "local-user")
                        .requestAttr("userNsRoles", Map.of(1L, NamespaceRole.OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "namespace": "global",
                                  "slug": "new-pack",
                                  "name": "New Pack",
                                  "visibility": "PUBLIC",
                                  "items": [{ "skillId": 1, "note": "Start here" }]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slug").value("new-pack"));

        verify(skillBundleAppService).create(any(), eq("local-user"), ArgumentMatchers.<Map<Long, NamespaceRole>>any());
    }

    @Test
    void createShouldRejectMissingRequiredFieldsBeforeServiceCall() throws Exception {
        mockMvc.perform(post("/api/web/skill-bundles")
                        .with(csrf())
                        .with(auth("local-user"))
                        .requestAttr("userId", "local-user")
                        .requestAttr("userNsRoles", Map.of(1L, NamespaceRole.OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slug": "new-pack",
                                  "visibility": "PUBLIC",
                                  "items": [{ "skillId": 1 }]
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateShouldUsePathIdentityAndRequestBody() throws Exception {
        when(skillBundleAppService.update(eq("global"), eq("old-pack"), any(), eq("local-user"), ArgumentMatchers.<Map<Long, NamespaceRole>>any()))
                .thenReturn(new SkillBundleDetailResponse(
                        8L,
                        "global",
                        "new-pack",
                        "New Pack",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "PUBLIC",
                        "DRAFT",
                        true,
                        0L,
                        List.of(),
                        List.of(),
                        Instant.parse("2026-07-28T00:00:00Z"),
                        Instant.parse("2026-07-28T00:00:00Z")
                ));

        mockMvc.perform(put("/api/web/skill-bundles/global/old-pack")
                        .with(csrf())
                        .with(auth("local-user"))
                        .requestAttr("userId", "local-user")
                        .requestAttr("userNsRoles", Map.of(1L, NamespaceRole.OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "namespace": "global",
                                  "slug": "new-pack",
                                  "name": "New Pack",
                                  "visibility": "PUBLIC",
                                  "items": [{ "skillId": 1 }]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slug").value("new-pack"));
    }

    private RequestPostProcessor auth(String userId) {
        PlatformPrincipal principal = new PlatformPrincipal(
                userId,
                userId,
                userId + "@example.com",
                "",
                "session",
                java.util.Set.of()
        );
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        return authentication(authenticationToken);
    }
}
