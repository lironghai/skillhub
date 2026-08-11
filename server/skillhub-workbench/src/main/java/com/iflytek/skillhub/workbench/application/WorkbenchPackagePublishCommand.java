package com.iflytek.skillhub.workbench.application;

import com.iflytek.skillhub.domain.skill.SkillVisibility;
import java.util.Set;

public record WorkbenchPackagePublishCommand(
        String confirmPackageFingerprint,
        SkillVisibility visibility,
        Set<String> platformRoles) {
}
