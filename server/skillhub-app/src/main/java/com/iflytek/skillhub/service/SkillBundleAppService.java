package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.label.LabelDefinition;
import com.iflytek.skillhub.domain.label.LabelDefinitionService;
import com.iflytek.skillhub.domain.label.LabelTranslation;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skillbundle.SkillBundle;
import com.iflytek.skillhub.domain.skillbundle.SkillBundleService;
import com.iflytek.skillhub.domain.skillbundle.SkillBundleStatus;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.SkillBundleDetailResponse;
import com.iflytek.skillhub.dto.SkillBundleDraftRequest;
import com.iflytek.skillhub.dto.SkillBundleItemRequest;
import com.iflytek.skillhub.dto.SkillBundleItemResponse;
import com.iflytek.skillhub.dto.SkillBundleSummaryResponse;
import com.iflytek.skillhub.dto.SkillLabelDto;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class SkillBundleAppService {

    private final SkillBundleService skillBundleService;
    private final LabelDefinitionService labelDefinitionService;
    private final LabelLocalizationService labelLocalizationService;

    public SkillBundleAppService(SkillBundleService skillBundleService,
                                 LabelDefinitionService labelDefinitionService,
                                 LabelLocalizationService labelLocalizationService) {
        this.skillBundleService = skillBundleService;
        this.labelDefinitionService = labelDefinitionService;
        this.labelLocalizationService = labelLocalizationService;
    }

    public PageResponse<SkillBundleSummaryResponse> search(String keyword,
                                                           String namespaceSlug,
                                                           String sort,
                                                           int page,
                                                           int size,
                                                           List<String> labelSlugs,
                                                           String userId,
                                                           Map<Long, NamespaceRole> userNsRoles) {
        Page<SkillBundleService.BundleDetail> result = skillBundleService.searchVisible(
                keyword,
                namespaceSlug,
                labelSlugs,
                userId,
                userNsRoles,
                PageRequest.of(Math.max(page, 0), Math.max(size, 1), resolveSort(sort))
        );
        return PageResponse.from(result.map(this::toSummary));
    }

    public SkillBundleDetailResponse detail(String namespaceSlug,
                                            String bundleSlug,
                                            String userId,
                                            Map<Long, NamespaceRole> userNsRoles) {
        return toDetail(skillBundleService.getVisibleDetail(namespaceSlug, bundleSlug, userId, userNsRoles));
    }

    public SkillBundleDetailResponse create(SkillBundleDraftRequest request,
                                            String operatorUserId,
                                            Map<Long, NamespaceRole> userNsRoles) {
        SkillBundle bundle = skillBundleService.createDraft(toCommand(request), operatorUserId, userNsRoles);
        return detail(request.namespace(), bundle.getSlug(), operatorUserId, userNsRoles);
    }

    public SkillBundleDetailResponse update(String namespaceSlug,
                                            String bundleSlug,
                                            SkillBundleDraftRequest request,
                                            String operatorUserId,
                                            Map<Long, NamespaceRole> userNsRoles) {
        SkillBundle bundle = skillBundleService.updateDraft(
                namespaceSlug,
                bundleSlug,
                toCommand(request),
                operatorUserId,
                userNsRoles
        );
        return detail(namespaceSlug, bundle.getSlug(), operatorUserId, userNsRoles);
    }

    private SkillBundleService.BundleDraftCommand toCommand(SkillBundleDraftRequest request) {
        return new SkillBundleService.BundleDraftCommand(
                request.namespace(),
                request.name(),
                request.slug(),
                request.summary(),
                request.avatarUrl(),
                request.description(),
                request.roleDescription(),
                request.applicableScenarios(),
                request.methodology(),
                request.recommendedSkillNotes(),
                parseVisibility(request.visibility()),
                parseStatus(request.status()),
                request.labels(),
                toItemCommands(request.items())
        );
    }

    private List<SkillBundleService.BundleItemCommand> toItemCommands(List<SkillBundleItemRequest> items) {
        if (items == null) {
            return List.of();
        }
        return items.stream()
                .map(item -> new SkillBundleService.BundleItemCommand(item.skillId(), null, item.note()))
                .toList();
    }

    private SkillVisibility parseVisibility(String visibility) {
        if (visibility == null || visibility.isBlank()) {
            return SkillVisibility.PRIVATE;
        }
        String normalized = visibility.trim().toUpperCase(Locale.ROOT);
        try {
            return SkillVisibility.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new DomainBadRequestException("error.skillBundle.visibility.invalid", visibility);
        }
    }

    private SkillBundleStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return SkillBundleStatus.DRAFT;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        try {
            return SkillBundleStatus.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new DomainBadRequestException("error.skillBundle.status.invalid", status);
        }
    }

    private Sort resolveSort(String sort) {
        if ("name".equals(sort)) {
            return Sort.by(Sort.Direction.ASC, "name").and(Sort.by(Sort.Direction.DESC, "id"));
        }
        return Sort.by(Sort.Direction.DESC, "updatedAt").and(Sort.by(Sort.Direction.DESC, "id"));
    }

    private SkillBundleSummaryResponse toSummary(SkillBundleService.BundleDetail detail) {
        SkillBundle bundle = detail.bundle();
        return new SkillBundleSummaryResponse(
                bundle.getId(),
                detail.namespaceSlug(),
                bundle.getSlug(),
                bundle.getName(),
                bundle.getSummary(),
                bundle.getAvatarUrl(),
                bundle.getRoleDescription(),
                bundle.getApplicableScenarios(),
                bundle.getVisibility().name(),
                bundle.getStatus().name(),
                detail.items().size(),
                bundle.getDownloadCount(),
                toLabelDtos(detail.labels()),
                bundle.getUpdatedAt()
        );
    }

    private SkillBundleDetailResponse toDetail(SkillBundleService.BundleDetail detail) {
        SkillBundle bundle = detail.bundle();
        return new SkillBundleDetailResponse(
                bundle.getId(),
                detail.namespaceSlug(),
                bundle.getSlug(),
                bundle.getName(),
                bundle.getSummary(),
                bundle.getAvatarUrl(),
                bundle.getDescription(),
                bundle.getRoleDescription(),
                bundle.getApplicableScenarios(),
                bundle.getMethodology(),
                bundle.getRecommendedSkillNotes(),
                bundle.getVisibility().name(),
                bundle.getStatus().name(),
                detail.canManage(),
                bundle.getDownloadCount(),
                toLabelDtos(detail.labels()),
                detail.items().stream()
                        .map(this::toItemResponse)
                        .toList(),
                bundle.getCreatedAt(),
                bundle.getUpdatedAt()
        );
    }

    private SkillBundleItemResponse toItemResponse(SkillBundleService.BundleItemDetail item) {
        return new SkillBundleItemResponse(
                item.skillId(),
                item.namespaceSlug(),
                item.skillSlug(),
                item.displayName(),
                item.sortOrder(),
                item.note()
        );
    }

    private List<SkillLabelDto> toLabelDtos(List<String> labelSlugs) {
        if (labelSlugs == null || labelSlugs.isEmpty()) {
            return List.of();
        }
        List<LabelDefinition> definitions = labelSlugs.stream()
                .filter(Objects::nonNull)
                .map(labelDefinitionService::getBySlug)
                .toList();
        Map<Long, List<LabelTranslation>> translationsByLabelId =
                labelDefinitionService.listTranslationsByLabelIds(definitions.stream().map(LabelDefinition::getId).toList());
        Map<String, LabelDefinition> definitionsBySlug = definitions.stream()
                .collect(Collectors.toMap(LabelDefinition::getSlug, Function.identity()));
        return labelSlugs.stream()
                .map(definitionsBySlug::get)
                .filter(Objects::nonNull)
                .map(label -> new SkillLabelDto(
                        label.getSlug(),
                        label.getType().name(),
                        labelLocalizationService.resolveDisplayName(
                                label.getSlug(),
                                translationsByLabelId.getOrDefault(label.getId(), List.of()))
                ))
                .toList();
    }
}
