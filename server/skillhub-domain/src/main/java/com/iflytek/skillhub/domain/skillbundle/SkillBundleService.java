package com.iflytek.skillhub.domain.skillbundle;

import com.iflytek.skillhub.domain.label.LabelDefinition;
import com.iflytek.skillhub.domain.label.LabelDefinitionRepository;
import com.iflytek.skillhub.domain.label.LabelType;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceService;
import com.iflytek.skillhub.domain.namespace.SlugValidator;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.VisibilityChecker;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SkillBundleService {

    public static final int MAX_BUNDLE_ITEM_COUNT = 50;

    private final SkillBundleRepository skillBundleRepository;
    private final SkillBundleItemRepository skillBundleItemRepository;
    private final SkillBundleLabelRepository skillBundleLabelRepository;
    private final NamespaceRepository namespaceRepository;
    private final NamespaceService namespaceService;
    private final SkillRepository skillRepository;
    private final LabelDefinitionRepository labelDefinitionRepository;
    private final VisibilityChecker visibilityChecker;

    public SkillBundleService(SkillBundleRepository skillBundleRepository,
                              SkillBundleItemRepository skillBundleItemRepository,
                              SkillBundleLabelRepository skillBundleLabelRepository,
                              NamespaceRepository namespaceRepository,
                              NamespaceService namespaceService,
                              SkillRepository skillRepository,
                              LabelDefinitionRepository labelDefinitionRepository,
                              VisibilityChecker visibilityChecker) {
        this.skillBundleRepository = skillBundleRepository;
        this.skillBundleItemRepository = skillBundleItemRepository;
        this.skillBundleLabelRepository = skillBundleLabelRepository;
        this.namespaceRepository = namespaceRepository;
        this.namespaceService = namespaceService;
        this.skillRepository = skillRepository;
        this.labelDefinitionRepository = labelDefinitionRepository;
        this.visibilityChecker = visibilityChecker;
    }

    public record BundleDraftCommand(
            String namespace,
            String name,
            String slug,
            String summary,
            String avatarUrl,
            String description,
            String roleDescription,
            String applicableScenarios,
            String methodology,
            String recommendedSkillNotes,
            SkillVisibility visibility,
            SkillBundleStatus status,
            List<String> labels,
            List<BundleItemCommand> items
    ) {
    }

    public record BundleItemCommand(Long skillId, Integer sortOrder, String note) {
    }

    public record BundleDetail(
            SkillBundle bundle,
            String namespaceSlug,
            List<String> labels,
            List<BundleItemDetail> items,
            boolean canManage
    ) {
    }

    public record BundleItemDetail(
            Long skillId,
            String namespaceSlug,
            String skillSlug,
            String displayName,
            Integer sortOrder,
            String note
    ) {
    }

    @Transactional
    public SkillBundle createDraft(BundleDraftCommand command,
                                   String operatorId,
                                   Map<Long, NamespaceRole> userNsRoles) {
        Namespace namespace = namespaceService.getNamespaceBySlugForRead(
                requireText(command.namespace(), "skill_bundle.namespace.required"),
                operatorId,
                safeRoles(userNsRoles));
        requireAdminOrOwner(namespace.getId(), operatorId, userNsRoles);
        String slug = normalizeAndValidateSlug(command.slug());
        if (skillBundleRepository.findByNamespaceIdAndSlug(namespace.getId(), slug).isPresent()) {
            throw new DomainBadRequestException("error.skillBundle.slug.exists", slug);
        }

        SkillBundle bundle = new SkillBundle(
                namespace.getId(),
                requireText(command.name(), "skill_bundle.name.required"),
                slug,
                operatorId,
                command.visibility() != null ? command.visibility() : SkillVisibility.PRIVATE
        );
        applyBasicInfo(bundle, command, operatorId);
        bundle.setCreatedBy(operatorId);
        bundle = skillBundleRepository.save(bundle);
        replaceItemsAndLabels(bundle, command, operatorId, namespace.getId());
        return bundle;
    }

    @Transactional
    public SkillBundle updateDraft(String namespaceSlug,
                                   String slug,
                                   BundleDraftCommand command,
                                   String operatorId,
                                   Map<Long, NamespaceRole> userNsRoles) {
        Namespace namespace = findNamespace(namespaceSlug);
        SkillBundle bundle = findBundle(namespace.getId(), slug);
        if (!canManage(bundle, operatorId, userNsRoles)) {
            throw new DomainForbiddenException("error.namespace.admin.required");
        }

        String newSlug = normalizeAndValidateSlug(command.slug() != null && !command.slug().isBlank() ? command.slug() : slug);
        if (!Objects.equals(newSlug, bundle.getSlug())
                && skillBundleRepository.findByNamespaceIdAndSlug(namespace.getId(), newSlug).isPresent()) {
            throw new DomainBadRequestException("error.skillBundle.slug.exists", newSlug);
        }
        applyBasicInfo(bundle, new BundleDraftCommand(
                namespaceSlug,
                command.name(),
                newSlug,
                command.summary(),
                command.avatarUrl(),
                command.description(),
                command.roleDescription(),
                command.applicableScenarios(),
                command.methodology(),
                command.recommendedSkillNotes(),
                command.visibility(),
                command.status(),
                command.labels(),
                command.items()
        ), operatorId);
        SkillBundle saved = skillBundleRepository.save(bundle);
        replaceItemsAndLabels(saved, command, operatorId, namespace.getId());
        return saved;
    }

    public BundleDetail getVisibleDetail(String namespaceSlug,
                                         String slug,
                                         String currentUserId,
                                         Map<Long, NamespaceRole> userNsRoles) {
        Namespace namespace = findNamespace(namespaceSlug);
        SkillBundle bundle = findBundle(namespace.getId(), slug);
        if (!canAccess(bundle, currentUserId, userNsRoles)) {
            throw new DomainForbiddenException("error.skillBundle.access.denied", slug);
        }
        return new BundleDetail(
                bundle,
                namespace.getSlug(),
                resolveLabelSlugs(bundle.getId()),
                resolveItemDetails(bundle.getId(), currentUserId, userNsRoles),
                canManage(bundle, currentUserId, userNsRoles)
        );
    }

    @Transactional
    public void recordDownload(SkillBundle bundle) {
        if (bundle == null || bundle.getId() == null) {
            return;
        }
        bundle.incrementDownloadCount();
        skillBundleRepository.incrementDownloadCount(bundle.getId());
    }

    public Page<BundleDetail> searchVisible(String keyword,
                                            String namespaceSlug,
                                            List<String> labelSlugs,
                                            String currentUserId,
                                            Map<Long, NamespaceRole> userNsRoles,
                                            Pageable pageable) {
        Long namespaceId = null;
        if (namespaceSlug != null && !namespaceSlug.isBlank()) {
            namespaceId = namespaceService.getNamespaceBySlugForRead(namespaceSlug, currentUserId, safeRoles(userNsRoles)).getId();
        }
        Set<Long> requiredLabelIds = resolveRequiredLabelIds(labelSlugs);
        List<SkillBundle> candidates = namespaceId == null
                ? skillBundleRepository.findAllByOrderByUpdatedAtDesc()
                : skillBundleRepository.findByNamespaceId(namespaceId);
        String normalizedKeyword = keyword == null ? null : keyword.trim().toLowerCase(Locale.ROOT);
        List<SkillBundle> filtered = candidates.stream()
                .filter(bundle -> canAccess(bundle, currentUserId, userNsRoles))
                .filter(bundle -> matchesKeyword(bundle, normalizedKeyword))
                .filter(bundle -> matchesLabels(bundle, requiredLabelIds))
                .sorted(resolveSearchComparator(pageable.getSort()))
                .toList();
        int start = (int) Math.min(pageable.getOffset(), filtered.size());
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        List<BundleDetail> pageItems = filtered.subList(start, end).stream()
                .map(bundle -> new BundleDetail(
                        bundle,
                        findNamespaceSlug(bundle.getNamespaceId()),
                        resolveLabelSlugs(bundle.getId()),
                        resolveItemDetails(bundle.getId(), currentUserId, userNsRoles),
                        canManage(bundle, currentUserId, userNsRoles)))
                .toList();
        return new PageImpl<>(pageItems, pageable, filtered.size());
    }

    private void applyBasicInfo(SkillBundle bundle, BundleDraftCommand command, String operatorId) {
        bundle.updateBasicInfo(
                requireText(command.name(), "skill_bundle.name.required"),
                normalizeAndValidateSlug(command.slug()),
                blankToNull(command.summary()),
                blankToNull(command.avatarUrl()),
                blankToNull(command.description()),
                blankToNull(command.roleDescription()),
                blankToNull(command.applicableScenarios()),
                blankToNull(command.methodology()),
                blankToNull(command.recommendedSkillNotes()),
                command.visibility() != null ? command.visibility() : SkillVisibility.PRIVATE,
                command.status() != null ? command.status() : SkillBundleStatus.DRAFT,
                operatorId
        );
    }

    private void replaceItemsAndLabels(SkillBundle bundle,
                                       BundleDraftCommand command,
                                       String operatorId,
                                       Long namespaceId) {
        skillBundleItemRepository.deleteByBundleId(bundle.getId());
        skillBundleItemRepository.flush();
        skillBundleLabelRepository.deleteByBundleId(bundle.getId());
        skillBundleLabelRepository.flush();

        List<SkillBundleItem> items = buildItems(
                bundle.getId(),
                namespaceId,
                command.items(),
                bundle.getStatus(),
                bundle.getVisibility());
        if (!items.isEmpty()) {
            skillBundleItemRepository.saveAll(items);
        }
        List<SkillBundleLabel> labels = buildLabels(bundle.getId(), command.labels(), operatorId);
        if (!labels.isEmpty()) {
            skillBundleLabelRepository.saveAll(labels);
        }
    }

    private List<SkillBundleItem> buildItems(Long bundleId,
                                             Long namespaceId,
                                             List<BundleItemCommand> commands,
                                             SkillBundleStatus bundleStatus,
                                             SkillVisibility bundleVisibility) {
        if (commands == null || commands.isEmpty()) {
            if (bundleStatus == SkillBundleStatus.PUBLISHED) {
                throw new DomainBadRequestException("error.skillBundle.published.items.required");
            }
            return List.of();
        }
        if (commands.size() > MAX_BUNDLE_ITEM_COUNT) {
            throw new DomainBadRequestException("error.skillBundle.items.tooMany", MAX_BUNDLE_ITEM_COUNT);
        }
        List<Long> skillIds = commands.stream()
                .map(BundleItemCommand::skillId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (skillIds.size() != commands.size()) {
            throw new DomainBadRequestException("error.skillBundle.item.invalid");
        }
        Map<Long, Skill> skillsById = skillRepository.findByIdIn(skillIds).stream()
                .collect(Collectors.toMap(Skill::getId, Function.identity()));
        List<SkillBundleItem> items = new ArrayList<>();
        for (int i = 0; i < commands.size(); i++) {
            BundleItemCommand item = commands.get(i);
            Skill skill = skillsById.get(item.skillId());
            if (skill == null || skill.getStatus() != SkillStatus.ACTIVE || !Objects.equals(skill.getNamespaceId(), namespaceId)) {
                throw new DomainBadRequestException("error.skillBundle.item.skillNotFound", item.skillId());
            }
            if (bundleStatus == SkillBundleStatus.PUBLISHED && !isPublishableItem(skill, bundleVisibility)) {
                throw new DomainBadRequestException("error.skillBundle.item.invalid", item.skillId());
            }
            items.add(new SkillBundleItem(
                    bundleId,
                    item.skillId(),
                    item.sortOrder() != null ? item.sortOrder() : i,
                    blankToNull(item.note())
            ));
        }
        return items;
    }

    private boolean isPublishableItem(Skill skill, SkillVisibility bundleVisibility) {
        if (skill.isHidden() || skill.getLatestVersionId() == null) {
            return false;
        }
        return switch (bundleVisibility) {
            case PUBLIC -> skill.getVisibility() == SkillVisibility.PUBLIC;
            case NAMESPACE_ONLY -> skill.getVisibility() == SkillVisibility.PUBLIC
                    || skill.getVisibility() == SkillVisibility.NAMESPACE_ONLY;
            case PRIVATE -> true;
        };
    }

    private List<SkillBundleLabel> buildLabels(Long bundleId, List<String> labelSlugs, String operatorId) {
        List<Long> labelIds = resolveRequiredLabelIds(labelSlugs).stream().toList();
        return labelIds.stream()
                .map(labelId -> new SkillBundleLabel(bundleId, labelId, operatorId))
                .toList();
    }

    private Set<Long> resolveRequiredLabelIds(List<String> labelSlugs) {
        if (labelSlugs == null || labelSlugs.isEmpty()) {
            return Set.of();
        }
        Set<Long> labelIds = new LinkedHashSet<>();
        for (String rawSlug : labelSlugs) {
            if (rawSlug == null || rawSlug.isBlank()) {
                continue;
            }
            String slug = rawSlug.trim().toLowerCase(Locale.ROOT);
            LabelDefinition definition = labelDefinitionRepository.findBySlugIgnoreCase(slug)
                    .orElseThrow(() -> new DomainBadRequestException("label.not_found", slug));
            if (definition.getType() != LabelType.RECOMMENDED || !definition.isVisibleInFilter()) {
                throw new DomainBadRequestException("error.skillBundle.label.invalid", slug);
            }
            labelIds.add(definition.getId());
        }
        return labelIds;
    }

    private boolean matchesLabels(SkillBundle bundle, Set<Long> requiredLabelIds) {
        if (requiredLabelIds.isEmpty()) {
            return true;
        }
        Set<Long> bundleLabelIds = skillBundleLabelRepository.findByBundleId(bundle.getId()).stream()
                .map(SkillBundleLabel::getLabelId)
                .collect(Collectors.toSet());
        return bundleLabelIds.containsAll(requiredLabelIds);
    }

    private boolean matchesKeyword(SkillBundle bundle, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        return contains(bundle.getName(), keyword)
                || contains(bundle.getSlug(), keyword)
                || contains(bundle.getSummary(), keyword)
                || contains(bundle.getRoleDescription(), keyword)
                || contains(bundle.getApplicableScenarios(), keyword)
                || contains(bundle.getMethodology(), keyword)
                || contains(bundle.getRecommendedSkillNotes(), keyword);
    }

    private Comparator<SkillBundle> resolveSearchComparator(Sort sort) {
        Sort.Order primaryOrder = sort == null ? null : sort.stream().findFirst().orElse(null);
        if (primaryOrder != null && "name".equals(primaryOrder.getProperty())) {
            return Comparator.comparing(
                            SkillBundle::getName,
                            Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                    .thenComparing(SkillBundle::getId, Comparator.reverseOrder());
        }
        return Comparator.comparing(SkillBundle::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(SkillBundle::getId, Comparator.reverseOrder());
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private List<BundleItemDetail> resolveItemDetails(Long bundleId,
                                                      String currentUserId,
                                                      Map<Long, NamespaceRole> userNsRoles) {
        List<SkillBundleItem> items = skillBundleItemRepository.findByBundleIdOrderBySortOrderAscIdAsc(bundleId).stream()
                .sorted(Comparator.comparing(SkillBundleItem::getSortOrder).thenComparing(item -> item.getId() != null ? item.getId() : 0L))
                .toList();
        if (items.isEmpty()) {
            return List.of();
        }
        List<Long> skillIds = items.stream().map(SkillBundleItem::getSkillId).toList();
        Map<Long, Skill> skillsById = skillRepository.findByIdIn(skillIds).stream()
                .collect(Collectors.toMap(Skill::getId, Function.identity()));
        Map<Long, String> namespaceSlugsById = namespaceRepository.findByIdIn(skillsById.values().stream()
                        .map(Skill::getNamespaceId)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(Namespace::getId, Namespace::getSlug));

        return items.stream()
                .map(item -> {
                    Skill skill = skillsById.get(item.getSkillId());
                    if (skill == null || !visibilityChecker.canAccess(skill, currentUserId, safeRoles(userNsRoles))) {
                        return null;
                    }
                    return new BundleItemDetail(
                            skill.getId(),
                            namespaceSlugsById.get(skill.getNamespaceId()),
                            skill.getSlug(),
                            skill.getDisplayName(),
                            item.getSortOrder(),
                            item.getNote()
                    );
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private List<String> resolveLabelSlugs(Long bundleId) {
        return skillBundleLabelRepository.findByBundleId(bundleId).stream()
                .map(SkillBundleLabel::getLabelId)
                .distinct()
                .map(labelDefinitionRepository::findById)
                .flatMap(java.util.Optional::stream)
                .sorted(Comparator.comparingInt(LabelDefinition::getSortOrder).thenComparing(LabelDefinition::getSlug))
                .map(LabelDefinition::getSlug)
                .toList();
    }

    private boolean canAccess(SkillBundle bundle, String currentUserId, Map<Long, NamespaceRole> userNsRoles) {
        Map<Long, NamespaceRole> roles = safeRoles(userNsRoles);
        boolean canManage = canManage(bundle, currentUserId, roles);
        if (bundle.getStatus() == SkillBundleStatus.ARCHIVED || bundle.getStatus() == SkillBundleStatus.DRAFT) {
            return canManage;
        }
        return switch (bundle.getVisibility()) {
            case PUBLIC -> true;
            case NAMESPACE_ONLY -> currentUserId != null && roles.containsKey(bundle.getNamespaceId());
            case PRIVATE -> canManage;
        };
    }

    private boolean canManage(SkillBundle bundle, String currentUserId, Map<Long, NamespaceRole> userNsRoles) {
        Map<Long, NamespaceRole> roles = safeRoles(userNsRoles);
        return isOwner(bundle, currentUserId) || isNamespaceAdminOrOwner(bundle.getNamespaceId(), roles);
    }

    private boolean isOwner(SkillBundle bundle, String currentUserId) {
        return currentUserId != null && currentUserId.equals(bundle.getOwnerId());
    }

    private void requireAdminOrOwner(Long namespaceId, String operatorId, Map<Long, NamespaceRole> userNsRoles) {
        if (operatorId == null || !isNamespaceAdminOrOwner(namespaceId, safeRoles(userNsRoles))) {
            throw new DomainForbiddenException("error.namespace.admin.required");
        }
    }

    private boolean isNamespaceAdminOrOwner(Long namespaceId, Map<Long, NamespaceRole> roles) {
        NamespaceRole role = roles.get(namespaceId);
        return role == NamespaceRole.ADMIN || role == NamespaceRole.OWNER;
    }

    private Namespace findNamespace(String namespaceSlug) {
        return namespaceRepository.findBySlug(namespaceSlug)
                .orElseThrow(() -> new DomainBadRequestException("error.namespace.slug.notFound", namespaceSlug));
    }

    private String findNamespaceSlug(Long namespaceId) {
        return namespaceRepository.findById(namespaceId)
                .map(Namespace::getSlug)
                .orElse(null);
    }

    private SkillBundle findBundle(Long namespaceId, String slug) {
        return skillBundleRepository.findByNamespaceIdAndSlug(namespaceId, slug)
                .orElseThrow(() -> new DomainBadRequestException("error.skillBundle.notFound", slug));
    }

    private Map<Long, NamespaceRole> safeRoles(Map<Long, NamespaceRole> userNsRoles) {
        return userNsRoles != null ? userNsRoles : Map.of();
    }

    private String normalizeAndValidateSlug(String rawSlug) {
        String slug = rawSlug == null ? null : rawSlug.trim().toLowerCase(Locale.ROOT);
        SlugValidator.validate(slug);
        return slug;
    }

    private String requireText(String value, String messageCode) {
        if (value == null || value.isBlank()) {
            throw new DomainBadRequestException(messageCode);
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
