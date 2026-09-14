package com.iflytek.skillhub.service.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.service.SkillLifecycleProjectionService;
import com.iflytek.skillhub.domain.skill.service.SkillQueryService;
import com.iflytek.skillhub.domain.skill.service.SkillSlugResolutionService;
import com.iflytek.skillhub.dto.SkillLifecycleVersionResponse;
import com.iflytek.skillhub.search.SearchQueryService;
import com.iflytek.skillhub.service.SkillSearchAppService;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.mock.web.MockHttpServletRequest;

class SkillHubMcpReadFacadeAdapterTest {

    private final SearchQueryService searchQueryService = mock(SearchQueryService.class);
    private final SkillRepository skillRepository = mock(SkillRepository.class);
    private final NamespaceRepository namespaceRepository = mock(NamespaceRepository.class);
    private final SkillSlugResolutionService skillSlugResolutionService = mock(SkillSlugResolutionService.class);
    private final SkillQueryService skillQueryService = mock(SkillQueryService.class);
    private final SkillSearchAppService skillSearchAppService = mock(SkillSearchAppService.class);

    private SkillHubMcpReadFacadeAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SkillHubMcpReadFacadeAdapter(
                skillSearchAppService,
                skillQueryService,
                skillRepository,
                namespaceRepository,
                skillSlugResolutionService);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("userId", "user-1");
        request.setAttribute("userNsRoles", Map.of(10L, NamespaceRole.ADMIN));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    void listSkillsUsesVisibleNamespaceAndCurrentViewerContext() {
        when(skillSearchAppService.search("pdf", "team-a", "newest", 1, 5, List.of(), "user-1", Map.of(10L, NamespaceRole.ADMIN)))
                .thenReturn(new SkillSearchAppService.SearchResponse(
                        List.of(new com.iflytek.skillhub.dto.SkillSummaryResponse(
                                42L,
                                "pdf-parser",
                                "PDF Parser",
                                "Parse PDFs",
                                "PUBLIC",
                                "ACTIVE",
                                7L,
                                2,
                                new BigDecimal("4.25"),
                                8,
                                "team-a",
                                Instant.EPOCH,
                                false,
                                new SkillLifecycleVersionResponse(99L, "1.2.0", "PUBLISHED"),
                                new SkillLifecycleVersionResponse(99L, "1.2.0", "PUBLISHED"),
                                null,
                                "PUBLISHED",
                                null)),
                        1L,
                        1,
                        5));

        var response = adapter.listSkills("pdf", "team-a", "newest", 1, 5);

        assertEquals(1L, response.total());
        assertEquals(7L, response.items().getFirst().downloadCount());
        assertEquals(new BigDecimal("4.25"), response.items().getFirst().ratingAvg());
        assertEquals(8, response.items().getFirst().ratingCount());
        verify(skillSearchAppService).search("pdf", "team-a", "newest", 1, 5, List.of(), "user-1", Map.of(10L, NamespaceRole.ADMIN));
    }

    @Test
    void getSkillDetailResolvesBySkillId() {
        Skill skill = new Skill(10L, "pdf-parser", "owner-1", SkillVisibility.PUBLIC);
        setField(skill, "id", 42L);
        Namespace namespace = new Namespace("global", "Global", "owner-1");
        setField(namespace, "id", 10L);
        when(skillRepository.findById(42L)).thenReturn(java.util.Optional.of(skill));
        when(namespaceRepository.findById(10L)).thenReturn(java.util.Optional.of(namespace));
        when(skillQueryService.getSkillDetail("global", "pdf-parser", "user-1", Map.of(10L, NamespaceRole.ADMIN)))
                .thenReturn(new SkillQueryService.SkillDetailDTO(
                        42L,
                        "pdf-parser",
                        "PDF Parser",
                        "owner-1",
                        "Owner One",
                        "Parse PDFs",
                        "PUBLIC",
                        "ACTIVE",
                        10L,
                        1,
                        0,
                        BigDecimal.ONE,
                        1,
                        false,
                        10L,
                        Instant.EPOCH,
                        Instant.EPOCH,
                        true,
                        true,
                        true,
                        false,
                        new SkillLifecycleProjectionService.VersionProjection(99L, "1.2.0", "PUBLISHED"),
                        new SkillLifecycleProjectionService.VersionProjection(99L, "1.2.0", "PUBLISHED"),
                        null,
                        null,
                        "PUBLISHED"));
        when(skillQueryService.getReviewSkillSnapshot(99L)).thenReturn(
                new SkillQueryService.ReviewSkillSnapshotDTO(
                        skill,
                        "Owner One",
                        new com.iflytek.skillhub.domain.skill.SkillVersion(42L, "1.2.0", "owner-1"),
                        null,
                        List.of(),
                        List.of(
                                new com.iflytek.skillhub.domain.skill.SkillFile(99L, "README.md", 10L, "text/markdown", "abc", "key-readme"),
                                new com.iflytek.skillhub.domain.skill.SkillFile(99L, "SKILL.md", 10L, "text/markdown", "def", "key-skill")),
                        "README.md",
                        "README content"));
        when(skillQueryService.getFileContentByVersionId(99L, "SKILL.md"))
                .thenReturn(new ByteArrayInputStream("skill content".getBytes(StandardCharsets.UTF_8)));

        var response = adapter.getSkillDetail(42L, null, null);

        assertEquals("global", response.namespace());
        assertEquals("pdf-parser", response.slug());
        assertEquals("SKILL.md", response.skillPath());
        assertEquals("skill content", response.skillContent());
        verify(skillQueryService).getSkillDetail("global", "pdf-parser", "user-1", Map.of(10L, NamespaceRole.ADMIN));
    }

    @Test
    void getSkillRefReturnsAllReferenceFiles() {
        stubSkillSnapshot(List.of(
                new com.iflytek.skillhub.domain.skill.SkillFile(99L, "references/faq.md", 10L, "text/markdown", "abc", "key-faq"),
                new com.iflytek.skillhub.domain.skill.SkillFile(99L, "references/links.md", 10L, "text/markdown", "def", "key-links"),
                new com.iflytek.skillhub.domain.skill.SkillFile(99L, "SKILL.md", 10L, "text/markdown", "ghi", "key-skill")));
        when(skillQueryService.getFileContentByVersionId(99L, "references/faq.md"))
                .thenReturn(new ByteArrayInputStream("faq content".getBytes(StandardCharsets.UTF_8)));
        when(skillQueryService.getFileContentByVersionId(99L, "references/links.md"))
                .thenReturn(new ByteArrayInputStream("links content".getBytes(StandardCharsets.UTF_8)));

        var response = adapter.getSkillRef(42L, null, null);

        assertEquals(2, response.references().size());
        assertEquals("references/faq.md", response.references().get(0).path());
        assertEquals("faq content", response.references().get(0).content());
        assertEquals("references/links.md", response.references().get(1).path());
        assertEquals("links content", response.references().get(1).content());
    }

    @Test
    void getSkillRefReturnsEmptyReferencesWhenNoReferenceFileExists() {
        stubSkillSnapshot(List.of(
                new com.iflytek.skillhub.domain.skill.SkillFile(99L, "docs.txt", 10L, "text/plain", "abc", "key-docs")));

        var response = adapter.getSkillRef(42L, null, null);

        assertTrue(response.references().isEmpty());
    }

    @Test
    void getSkillRefRejectsReferencesAboveResponseLimitBeforeReadingStorage() {
        stubSkillSnapshot(List.of(new com.iflytek.skillhub.domain.skill.SkillFile(
                99L,
                "references/large.md",
                (long) SkillHubMcpReadFacadeAdapter.MAX_REFERENCE_BYTES + 1,
                "text/markdown",
                "abc",
                "key-large")));

        assertThrows(IllegalArgumentException.class, () -> adapter.getSkillRef(42L, null, null));
        verify(skillQueryService, never()).getFileContentByVersionId(99L, "references/large.md");
    }

    @Test
    void getSkillDetailRejectsOversizedSkillFileBeforeReadingStorage() {
        stubSkillSnapshot(List.of(new com.iflytek.skillhub.domain.skill.SkillFile(
                99L,
                "SKILL.md",
                (long) SkillHubMcpReadFacadeAdapter.MAX_SKILL_CONTENT_BYTES + 1,
                "text/markdown",
                "abc",
                "key-skill")));

        assertThrows(IllegalArgumentException.class, () -> adapter.getSkillDetail(42L, null, null));
        verify(skillQueryService, never()).getFileContentByVersionId(99L, "SKILL.md");
    }

    private void stubSkillSnapshot(List<com.iflytek.skillhub.domain.skill.SkillFile> files) {
        Skill skill = new Skill(10L, "pdf-parser", "owner-1", SkillVisibility.PUBLIC);
        setField(skill, "id", 42L);
        Namespace namespace = new Namespace("global", "Global", "owner-1");
        setField(namespace, "id", 10L);
        when(skillRepository.findById(42L)).thenReturn(java.util.Optional.of(skill));
        when(namespaceRepository.findById(10L)).thenReturn(java.util.Optional.of(namespace));
        when(skillQueryService.getSkillDetail("global", "pdf-parser", "user-1", Map.of(10L, NamespaceRole.ADMIN)))
                .thenReturn(new SkillQueryService.SkillDetailDTO(
                        42L,
                        "pdf-parser",
                        "PDF Parser",
                        "owner-1",
                        "Owner One",
                        "Parse PDFs",
                        "PUBLIC",
                        "ACTIVE",
                        10L,
                        1,
                        0,
                        BigDecimal.ONE,
                        1,
                        false,
                        10L,
                        Instant.EPOCH,
                        Instant.EPOCH,
                        true,
                        true,
                        true,
                        false,
                        new SkillLifecycleProjectionService.VersionProjection(99L, "1.2.0", "PUBLISHED"),
                        new SkillLifecycleProjectionService.VersionProjection(99L, "1.2.0", "PUBLISHED"),
                        null,
                        null,
                        "PUBLISHED"));
        when(skillQueryService.getReviewSkillSnapshot(99L)).thenReturn(
                new SkillQueryService.ReviewSkillSnapshotDTO(
                        skill,
                        "Owner One",
                        new com.iflytek.skillhub.domain.skill.SkillVersion(42L, "1.2.0", "owner-1"),
                        null,
                        List.of(),
                        files,
                        null,
                        null));
    }

    @SuppressWarnings("java:S3011")
    private static void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
