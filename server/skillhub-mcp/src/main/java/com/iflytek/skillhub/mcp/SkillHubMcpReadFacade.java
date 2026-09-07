package com.iflytek.skillhub.mcp;

public interface SkillHubMcpReadFacade {

    SkillHubMcpListSkillsResponse listSkills(String query, String namespace, String sort, int page, int size);

    SkillHubMcpSkillDetailResponse getSkillDetail(Long skillId, String namespace, String slug);

    SkillHubMcpSkillRefResponse getSkillRef(Long skillId, String namespace, String slug);
}
