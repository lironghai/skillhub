package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.domain.label.LabelDefinition;
import com.iflytek.skillhub.domain.label.LabelDefinitionService;
import com.iflytek.skillhub.domain.label.LabelTranslation;
import com.iflytek.skillhub.domain.label.LabelType;
import com.iflytek.skillhub.domain.label.SkillLabel;
import com.iflytek.skillhub.domain.label.SkillLabelService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceService;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.service.SkillLifecycleProjectionService;
import com.iflytek.skillhub.dto.SkillLabelDto;
import com.iflytek.skillhub.dto.SkillSummaryResponse;
import com.iflytek.skillhub.search.SearchQuery;
import com.iflytek.skillhub.search.SearchQueryService;
import com.iflytek.skillhub.search.SearchResult;
import com.iflytek.skillhub.search.SearchVisibilityScope;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Application service that assembles discovery responses from search matches.
 *
 * <p>{@link com.iflytek.skillhub.search.SearchQueryService} remains the match
 * engine, while this service enriches matched ids into API-facing summaries.
 * Authoritative detail, version, and file reads remain in
 * {@link com.iflytek.skillhub.domain.skill.service.SkillQueryService}.
 */
@Service
public class SkillSearchAppService {

    private final SearchQueryService searchQueryService;
    private final SkillRepository skillRepository;
    private final NamespaceRepository namespaceRepository;
    private final NamespaceService namespaceService;
    private final SkillLifecycleProjectionService skillLifecycleProjectionService;
    private final RbacService rbacService;
    private final SkillLabelService skillLabelService;
    private final LabelDefinitionService labelDefinitionService;
    private final LabelLocalizationService labelLocalizationService;

    public SkillSearchAppService(
            SearchQueryService searchQueryService,
            SkillRepository skillRepository,
            NamespaceRepository namespaceRepository,
            NamespaceService namespaceService,
            SkillLifecycleProjectionService skillLifecycleProjectionService,
            RbacService rbacService,
            SkillLabelService skillLabelService,
            LabelDefinitionService labelDefinitionService,
            LabelLocalizationService labelLocalizationService) {
        this.searchQueryService = searchQueryService;
        this.skillRepository = skillRepository;
        this.namespaceRepository = namespaceRepository;
        this.namespaceService = namespaceService;
        this.skillLifecycleProjectionService = skillLifecycleProjectionService;
        this.rbacService = rbacService;
        this.skillLabelService = skillLabelService;
        this.labelDefinitionService = labelDefinitionService;
        this.labelLocalizationService = labelLocalizationService;
    }

    public record SearchResponse(
            List<SkillSummaryResponse> items,
            long total,
            int page,
            int size
    ) {}

    public SearchResponse search(
            String keyword,
            String namespaceSlug,
            String sortBy,
            int page,
            int size,
            String userId,
            Map<Long, NamespaceRole> userNsRoles) {
        return search(keyword, namespaceSlug, sortBy, page, size, List.of(), userId, userNsRoles);
    }

    public SearchResponse search(
            String keyword,
            String namespaceSlug,
            String sortBy,
            int page,
            int size,
            List<String> labelSlugs,
            String userId,
            Map<Long, NamespaceRole> userNsRoles) {

        Long namespaceId = resolveNamespaceId(namespaceSlug, userId, userNsRoles);

        SearchVisibilityScope scope = buildVisibilityScope(userId, userNsRoles);

        return searchVisibleSkills(keyword, namespaceId, sortBy != null ? sortBy : "newest", page, size, labelSlugs, scope, false);
    }

    public SearchResponse searchInstallableLatest(
            String keyword,
            String namespaceSlug,
            String sortBy,
            int page,
            int size,
            String userId,
            Map<Long, NamespaceRole> userNsRoles) {
        Long namespaceId = resolveNamespaceId(namespaceSlug, userId, userNsRoles);
        SearchVisibilityScope scope = buildVisibilityScope(userId, userNsRoles);
        return searchVisibleSkills(keyword, namespaceId, sortBy != null ? sortBy : "newest", page, size, List.of(), scope, true);
    }

    private Long resolveNamespaceId(String namespaceSlug, String userId, Map<Long, NamespaceRole> userNsRoles) {
        if (namespaceSlug == null || namespaceSlug.isBlank()) {
            return null;
        }
        return namespaceService.getNamespaceBySlugForRead(namespaceSlug, userId, userNsRoles != null ? userNsRoles : Map.of()).getId();
    }

    private SearchVisibilityScope buildVisibilityScope(String userId, Map<Long, NamespaceRole> userNsRoles) {
        if (userId == null) {
            return SearchVisibilityScope.anonymous();
        }

        Map<Long, NamespaceRole> normalizedRoles = userNsRoles != null ? userNsRoles : Map.of();
        Set<Long> memberNamespaceIds = normalizedRoles.keySet();
        Set<Long> adminNamespaceIds = normalizedRoles.entrySet().stream()
                .filter(e -> e.getValue() == NamespaceRole.ADMIN)
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
        adminNamespaceIds.addAll(normalizedRoles.entrySet().stream()
                .filter(e -> e.getValue() == NamespaceRole.OWNER)
                .map(Map.Entry::getKey)
                .toList());

        Set<String> platformRoles = rbacService.getUserRoleCodes(userId);

        return new SearchVisibilityScope(
                userId,
                memberNamespaceIds,
                adminNamespaceIds,
                hasPlatformWideReadAccess(platformRoles)
        );
    }

    private boolean hasPlatformWideReadAccess(Set<String> platformRoles) {
        // Super admins should use a dedicated admin interface, not the public portal
        return false;
    }

    private SearchResponse searchVisibleSkills(
            String keyword,
            Long namespaceId,
            String sortBy,
            int page,
            int size,
            List<String> labelSlugs,
            SearchVisibilityScope scope,
            boolean requireInstallableLatest) {
        SearchResult result = searchQueryService.search(new SearchQuery(
                keyword,
                namespaceId,
                scope,
                sortBy,
                page,
                size,
                normalizeLabelSlugs(labelSlugs),
                requireInstallableLatest
        ));
        List<SkillSummaryResponse> pageItems = mapVisibleSkillSummaries(result.skillIds());
        return new SearchResponse(pageItems, result.total(), page, size);
    }

    private List<String> normalizeLabelSlugs(List<String> labelSlugs) {
        if (labelSlugs == null || labelSlugs.isEmpty()) {
            return List.of();
        }
        return labelSlugs.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(java.util.Locale.ROOT))
                .distinct()
                .toList();
    }

    private List<SkillSummaryResponse> mapVisibleSkillSummaries(List<Long> skillIds) {
        if (skillIds.isEmpty()) {
            return List.of();
        }

        List<Skill> matchedSkills = skillRepository.findByIdIn(skillIds);
        Map<Long, Skill> skillsById = matchedSkills.stream()
                .collect(Collectors.toMap(Skill::getId, Function.identity()));

        List<Long> namespaceIds = matchedSkills.stream()
                .map(Skill::getNamespaceId)
                .distinct()
                .toList();
        Map<Long, Namespace> namespacesById = namespaceIds.isEmpty()
                ? Map.of()
                : namespaceRepository.findByIdIn(namespaceIds).stream()
                .collect(Collectors.toMap(Namespace::getId, Function.identity()));
        Map<Long, String> namespaceSlugsById = namespacesById.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getSlug()));
        Map<Long, SkillLifecycleProjectionService.Projection> projectionsBySkillId =
                skillLifecycleProjectionService.projectPublishedSummaries(matchedSkills);
        Map<Long, List<SkillLabelDto>> labelsBySkillId = loadSummaryLabels(skillIds);

        return skillIds.stream()
                .map(skillsById::get)
                .filter(java.util.Objects::nonNull)
                .map(skill -> toSummaryResponse(
                        skill,
                        namespaceSlugsById,
                        projectionsBySkillId.get(skill.getId()),
                        labelsBySkillId.getOrDefault(skill.getId(), List.of())))
                .toList();
    }

    private SkillSummaryResponse toSummaryResponse(
            Skill skill,
            Map<Long, String> namespaceSlugsById,
            SkillLifecycleProjectionService.Projection projection,
            List<SkillLabelDto> labels) {
        String namespaceSlug = namespaceSlugsById.get(skill.getNamespaceId());

        return new SkillSummaryResponse(
                skill.getId(),
                skill.getSlug(),
                skill.getDisplayName(),
                skill.getSummary(),
                skill.getVisibility().name(),
                skill.getStatus().name(),
                skill.getDownloadCount(),
                skill.getStarCount(),
                skill.getRatingAvg(),
                skill.getRatingCount(),
                namespaceSlug,
                skill.getUpdatedAt(),
                false,
                toLifecycleVersion(projection.headlineVersion()),
                toLifecycleVersion(projection.publishedVersion()),
                toLifecycleVersion(projection.ownerPreviewVersion()),
                projection.resolutionMode().name(),
                labels
        );
    }

    private Map<Long, List<SkillLabelDto>> loadSummaryLabels(List<Long> skillIds) {
        List<SkillLabel> skillLabels = skillLabelService.listSkillLabelsBySkillIds(skillIds);
        if (skillLabels == null || skillLabels.isEmpty()) {
            return Map.of();
        }

        List<Long> labelIds = skillLabels.stream()
                .map(SkillLabel::getLabelId)
                .distinct()
                .toList();
        Map<Long, LabelDefinition> definitionsById = labelDefinitionService.listByIds(labelIds).stream()
                .collect(Collectors.toMap(LabelDefinition::getId, Function.identity()));
        Map<Long, List<LabelTranslation>> translationsByLabelId =
                labelDefinitionService.listTranslationsByLabelIds(labelIds);

        return skillLabels.stream()
                .filter(skillLabel -> isSummaryVisible(definitionsById.get(skillLabel.getLabelId())))
                .collect(Collectors.groupingBy(
                        SkillLabel::getSkillId,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                labels -> labels.stream()
                                        .sorted(Comparator
                                                .comparingInt((SkillLabel skillLabel) -> definitionsById
                                                        .get(skillLabel.getLabelId())
                                                        .getSortOrder())
                                                .thenComparing(skillLabel -> definitionsById
                                                        .get(skillLabel.getLabelId())
                                                        .getId()))
                                        .map(skillLabel -> toLabelDto(
                                                definitionsById.get(skillLabel.getLabelId()),
                                                translationsByLabelId))
                                        .toList()
                        )
                ));
    }

    private boolean isSummaryVisible(LabelDefinition definition) {
        return definition != null
                && definition.getType() == LabelType.RECOMMENDED
                && definition.isVisibleInFilter();
    }

    private SkillLabelDto toLabelDto(LabelDefinition definition, Map<Long, List<LabelTranslation>> translationsByLabelId) {
        return new SkillLabelDto(
                definition.getSlug(),
                definition.getType().name(),
                labelLocalizationService.resolveDisplayName(
                        definition.getSlug(),
                        translationsByLabelId.getOrDefault(definition.getId(), List.of()))
        );
    }

    private com.iflytek.skillhub.dto.SkillLifecycleVersionResponse toLifecycleVersion(
            SkillLifecycleProjectionService.VersionProjection projection) {
        if (projection == null) {
            return null;
        }
        return new com.iflytek.skillhub.dto.SkillLifecycleVersionResponse(
                projection.id(),
                projection.version(),
                projection.status()
        );
    }

}
