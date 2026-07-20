package com.iflytek.skillhub.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;

class SkillHubMcpToolsTest {

    @Test
    void listSkillsDelegatesNamespaceAndPaging() {
        SkillHubMcpReadFacade facade = mock(SkillHubMcpReadFacade.class);
        SkillHubMcpTools tools = new SkillHubMcpTools(facade);
        when(facade.listSkills("pdf", "team-a", "newest", 0, 20)).thenReturn(
                new SkillHubMcpListSkillsResponse(
                        List.of(new SkillHubMcpSkillSummary(
                                1L,
                                "team-a",
                                "pdf-parser",
                                "PDF Parser",
                                "Parse PDFs",
                                "PUBLIC",
                                "ACTIVE",
                                "1.2.0",
                                "1.2.0",
                                Instant.EPOCH,
                                12L,
                                new BigDecimal("4.5"),
                                2,
                                "PUBLISHED")),
                        1L,
                        0,
                        20));

        SkillHubMcpListSkillsResponse response = tools.listSkills("pdf", "team-a", "newest", null, null);

        assertEquals("team-a", response.items().getFirst().namespace());
        assertEquals(12L, response.items().getFirst().downloadCount());
        assertEquals(new BigDecimal("4.5"), response.items().getFirst().ratingAvg());
        assertEquals(2, response.items().getFirst().ratingCount());
        verify(facade).listSkills("pdf", "team-a", "newest", 0, 20);
    }

    @Test
    void toolNamesDoNotUseSkillhubPrefix() throws Exception {
        assertEquals("list_skills", toolName("listSkills", String.class, String.class, String.class, Integer.class, Integer.class));
        assertEquals("get_skill_detail", toolName("getSkillDetail", Long.class, String.class, String.class));
        assertEquals("get_skill_ref", toolName("getSkillRef", Long.class, String.class, String.class));
        assertFalse(toolName("listSkills", String.class, String.class, String.class, Integer.class, Integer.class)
                .startsWith("skillhub_"));
    }

    @Test
    void getSkillDetailRejectsSlugOnlyLookup() {
        SkillHubMcpTools tools = new SkillHubMcpTools(mock(SkillHubMcpReadFacade.class));

        assertThrows(IllegalArgumentException.class, () ->
                tools.getSkillDetail(null, "global", null));
    }

    @Test
    void getSkillRefRejectsSlugOnlyLookup() {
        SkillHubMcpTools tools = new SkillHubMcpTools(mock(SkillHubMcpReadFacade.class));

        assertThrows(IllegalArgumentException.class, () ->
                tools.getSkillRef(null, "global", null));
    }

    @Test
    void getSkillDetailDelegatesResolvedSkillId() {
        SkillHubMcpReadFacade facade = mock(SkillHubMcpReadFacade.class);
        SkillHubMcpTools tools = new SkillHubMcpTools(facade);
        when(facade.getSkillDetail(42L, null, null)).thenReturn(
                new SkillHubMcpSkillDetailResponse(
                        42L,
                        "global",
                        "pdf-parser",
                        10L,
                        "1.2.0",
                        "SKILL.md",
                        "skill content",
                        "PUBLISHED"));

        SkillHubMcpSkillDetailResponse response = tools.getSkillDetail(42L, null, null);

        assertEquals("SKILL.md", response.skillPath());
        assertEquals("skill content", response.skillContent());
        verify(facade).getSkillDetail(42L, null, null);
    }

    private String toolName(String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        return SkillHubMcpTools.class.getMethod(methodName, parameterTypes)
                .getAnnotation(Tool.class)
                .name();
    }
}
