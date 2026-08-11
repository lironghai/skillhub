package com.iflytek.skillhub.workbench.adapter;

import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.service.SkillPublishService;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.workbench.application.WorkbenchPackagePublishResult;
import com.iflytek.skillhub.workbench.application.WorkbenchPublishValidationResult;
import com.iflytek.skillhub.workbench.port.WorkbenchSkillPublishPort;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
class WorkbenchSkillPublishAdapter implements WorkbenchSkillPublishPort {

    private final NamespaceRepository namespaceRepository;
    private final SkillPublishService skillPublishService;

    WorkbenchSkillPublishAdapter(NamespaceRepository namespaceRepository,
                                  SkillPublishService skillPublishService) {
        this.namespaceRepository = namespaceRepository;
        this.skillPublishService = skillPublishService;
    }

    @Override
    public WorkbenchPublishValidationResult validateOnly(Long namespaceId, List<PackageEntry> entries,
                                                         String publisherId, SkillVisibility visibility,
                                                         Set<String> platformRoles) {
        String namespaceSlug = namespaceSlug(namespaceId);
        SkillPublishService.DryRunResult dryRun = skillPublishService.validateOnly(
                namespaceSlug,
                entries,
                publisherId,
                visibility,
                platformRoles);
        return new WorkbenchPublishValidationResult(
                dryRun.valid(),
                dryRun.errors(),
                dryRun.warnings(),
                dryRun.resolvedSlug(),
                dryRun.resolvedVersion());
    }

    @Override
    public WorkbenchPackagePublishResult publish(Long namespaceId, List<PackageEntry> entries,
                                                 String publisherId, SkillVisibility visibility,
                                                 Set<String> platformRoles) {
        String namespaceSlug = namespaceSlug(namespaceId);
        SkillPublishService.PublishResult result = skillPublishService.publishWorkbenchFromEntries(
                namespaceSlug,
                entries,
                publisherId,
                visibility,
                platformRoles);
        return new WorkbenchPackagePublishResult(
                result.skillId(),
                result.version().getId(),
                namespaceSlug,
                result.slug(),
                result.version().getVersion(),
                result.version().getStatus().name());
    }

    private String namespaceSlug(Long namespaceId) {
        return namespaceRepository.findById(namespaceId)
                .orElseThrow(() -> new DomainBadRequestException("error.namespace.notFound", namespaceId))
                .getSlug();
    }
}
