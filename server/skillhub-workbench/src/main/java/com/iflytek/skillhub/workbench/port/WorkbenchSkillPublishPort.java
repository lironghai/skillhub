package com.iflytek.skillhub.workbench.port;

import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.workbench.application.WorkbenchPackagePublishResult;
import com.iflytek.skillhub.workbench.application.WorkbenchPublishValidationResult;
import java.util.List;
import java.util.Set;

public interface WorkbenchSkillPublishPort {

    WorkbenchPublishValidationResult validateOnly(Long namespaceId, List<PackageEntry> entries,
                                                  String publisherId, SkillVisibility visibility,
                                                  Set<String> platformRoles);

    WorkbenchPackagePublishResult publish(Long namespaceId, List<PackageEntry> entries,
                                          String publisherId, SkillVisibility visibility,
                                          Set<String> platformRoles);
}
