package com.iflytek.skillhub.mcp;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

@Service
public class SkillHubMcpTools {

    private final SkillHubMcpReadFacade readFacade;

    public SkillHubMcpTools(SkillHubMcpReadFacade readFacade) {
        this.readFacade = readFacade;
    }

    @Tool(name = "list_skills", description = "List SkillHub skills visible to the current user.")
    public SkillHubMcpListSkillsResponse listSkills(
            @ToolParam(description = "Search keyword.", required = false) String query,
            @ToolParam(description = "Namespace slug to scope the search.", required = false) String namespace,
            @ToolParam(description = "Sort order, defaults to newest.", required = false) String sort,
            @ToolParam(description = "Zero-based page index.", required = false) Integer page,
            @ToolParam(description = "Page size.", required = false) Integer size) {
        return readFacade.listSkills(query, namespace, sort, normalizePage(page), normalizeSize(size));
    }

    @Tool(name = "get_skill_detail", description = "Get SKILL.md content for one visible SkillHub skill.")
    public SkillHubMcpSkillDetailResponse getSkillDetail(
            @ToolParam(description = "Unique skill id.", required = false) Long skillId,
            @ToolParam(description = "Namespace slug when skillId is omitted.", required = false) String namespace,
            @ToolParam(description = "Skill slug when skillId is omitted.", required = false) String slug) {
        validateLookup(skillId, namespace, slug);
        return readFacade.getSkillDetail(skillId, namespace, slug);
    }

    @Tool(name = "get_skill_ref", description = "Get files under references/ for one visible SkillHub skill.")
    public SkillHubMcpSkillRefResponse getSkillRef(
            @ToolParam(description = "Unique skill id.", required = false) Long skillId,
            @ToolParam(description = "Namespace slug when skillId is omitted.", required = false) String namespace,
            @ToolParam(description = "Skill slug when skillId is omitted.", required = false) String slug) {
        validateLookup(skillId, namespace, slug);
        return readFacade.getSkillRef(skillId, namespace, slug);
    }

    private void validateLookup(Long skillId, String namespace, String slug) {
        if (skillId == null && (isBlank(namespace) || isBlank(slug))) {
            throw new IllegalArgumentException("skillId or namespace+slug is required");
        }
    }

    private int normalizePage(Integer page) {
        return page == null || page < 0 ? 0 : page;
    }

    private int normalizeSize(Integer size) {
        if (size == null || size <= 0) {
            return 20;
        }
        return Math.min(size, 50);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
