package com.iflytek.skillhub.workbench.domain;

public record WorkbenchSourceVersion(Long skillId, Long versionId) {

    public WorkbenchSourceVersion {
        WorkbenchSession.requirePositive(skillId, "sourceSkillId");
        WorkbenchSession.requirePositive(versionId, "sourceVersionId");
    }
}
