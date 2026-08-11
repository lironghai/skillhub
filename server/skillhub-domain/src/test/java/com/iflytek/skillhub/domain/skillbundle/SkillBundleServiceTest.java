package com.iflytek.skillhub.domain.skillbundle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.iflytek.skillhub.domain.label.LabelDefinition;
import com.iflytek.skillhub.domain.label.LabelDefinitionRepository;
import com.iflytek.skillhub.domain.label.LabelType;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceService;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.VisibilityChecker;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SkillBundleServiceTest {

    @Mock
    private SkillBundleRepository skillBundleRepository;

    @Mock
    private SkillBundleItemRepository skillBundleItemRepository;

    @Mock
    private SkillBundleLabelRepository skillBundleLabelRepository;

    @Mock
    private NamespaceRepository namespaceRepository;

    @Mock
    private NamespaceService namespaceService;

    @Mock
    private SkillRepository skillRepository;

    @Mock
    private LabelDefinitionRepository labelDefinitionRepository;

    private SkillBundleService service;

    @BeforeEach
    void setUp() {
        service = new SkillBundleService(
                skillBundleRepository,
                skillBundleItemRepository,
                skillBundleLabelRepository,
                namespaceRepository,
                namespaceService,
                skillRepository,
                labelDefinitionRepository,
                new VisibilityChecker()
        );
    }

    @Test
    void createDraft_shouldPersistBundleMetadataItemsAndLabelsWithoutChangingSkills() {
        Namespace namespace = namespace(7L, "team-ai");
        Skill firstSkill = skill(101L, 7L, "planner", "owner-1", SkillVisibility.PUBLIC);
        Skill secondSkill = skill(102L, 7L, "writer", "owner-1", SkillVisibility.PUBLIC);
        LabelDefinition official = label(501L, "official");

        given(namespaceService.getNamespaceBySlugForRead("team-ai", "owner-1", Map.of(7L, NamespaceRole.OWNER)))
                .willReturn(namespace);
        given(skillBundleRepository.findByNamespaceIdAndSlug(7L, "expert-copywriter"))
                .willReturn(Optional.empty());
        given(skillBundleRepository.save(any(SkillBundle.class))).willAnswer(invocation -> {
            SkillBundle bundle = invocation.getArgument(0);
            setField(bundle, "id", 20L);
            return bundle;
        });
        given(skillRepository.findByIdIn(List.of(101L, 102L))).willReturn(List.of(firstSkill, secondSkill));
        given(labelDefinitionRepository.findBySlugIgnoreCase("official")).willReturn(Optional.of(official));

        SkillBundleService.BundleDraftCommand command = new SkillBundleService.BundleDraftCommand(
                "team-ai",
                "Expert Copywriter",
                "expert-copywriter",
                "A package for copy workflows",
                "https://cdn.example.com/expert.png",
                "Long form guidance",
                "Senior copy expert",
                "Campaigns and landing pages",
                "Analyze, draft, review",
                "Use planner before writer",
                SkillVisibility.NAMESPACE_ONLY,
                SkillBundleStatus.DRAFT,
                List.of("official"),
                List.of(
                        new SkillBundleService.BundleItemCommand(101L, 0, "Plan first"),
                        new SkillBundleService.BundleItemCommand(102L, 1, "Draft second")
                )
        );

        SkillBundle result = service.createDraft(command, "owner-1", Map.of(7L, NamespaceRole.OWNER));

        assertThat(result.getId()).isEqualTo(20L);
        assertThat(result.getNamespaceId()).isEqualTo(7L);
        assertThat(result.getName()).isEqualTo("Expert Copywriter");
        assertThat(result.getAvatarUrl()).isEqualTo("https://cdn.example.com/expert.png");
        assertThat(result.getVisibility()).isEqualTo(SkillVisibility.NAMESPACE_ONLY);
        verify(skillBundleItemRepository).deleteByBundleId(20L);
        verify(skillBundleItemRepository).saveAll(List.of(
                new SkillBundleItem(20L, 101L, 0, "Plan first"),
                new SkillBundleItem(20L, 102L, 1, "Draft second")
        ));
        verify(skillBundleLabelRepository).saveAll(List.of(new SkillBundleLabel(20L, 501L, "owner-1")));
        verify(skillRepository, never()).save(any(Skill.class));
    }

    @Test
    void updateDraft_shouldRejectNonOwnerNamespaceMember() {
        Namespace namespace = namespace(7L, "team-ai");
        SkillBundle bundle = bundle(20L, 7L, "expert-copywriter", "owner-2", SkillVisibility.PRIVATE);

        given(namespaceRepository.findBySlug("team-ai")).willReturn(Optional.of(namespace));
        given(skillBundleRepository.findByNamespaceIdAndSlug(7L, "expert-copywriter")).willReturn(Optional.of(bundle));

        SkillBundleService.BundleDraftCommand command = minimalCommand("team-ai", "expert-copywriter");

        assertThatThrownBy(() -> service.updateDraft("team-ai", "expert-copywriter", command, "member-1", Map.of(7L, NamespaceRole.MEMBER)))
                .isInstanceOf(DomainForbiddenException.class);
    }

    @Test
    void updateDraft_shouldAllowBundleOwnerWithoutNamespaceAdminRole() {
        Namespace namespace = namespace(7L, "team-ai");
        SkillBundle bundle = bundle(20L, 7L, "expert-copywriter", "owner-1", SkillVisibility.PRIVATE);

        given(namespaceRepository.findBySlug("team-ai")).willReturn(Optional.of(namespace));
        given(skillBundleRepository.findByNamespaceIdAndSlug(7L, "expert-copywriter")).willReturn(Optional.of(bundle));
        given(skillBundleRepository.save(bundle)).willReturn(bundle);

        SkillBundle result = service.updateDraft(
                "team-ai",
                "expert-copywriter",
                minimalCommand("team-ai", "expert-copywriter", List.of()),
                "owner-1",
                Map.of(7L, NamespaceRole.MEMBER)
        );

        assertThat(result).isSameAs(bundle);
        verify(skillBundleItemRepository).deleteByBundleId(20L);
    }

    @Test
    void updateDraft_shouldFlushDeletedItemsBeforeReinsertingReplacementItems() {
        Namespace namespace = namespace(7L, "team-ai");
        SkillBundle bundle = bundle(20L, 7L, "expert-copywriter", "owner-1", SkillVisibility.PRIVATE);
        Skill planner = skill(101L, 7L, "planner", "owner-1", SkillVisibility.PUBLIC);
        Skill writer = skill(102L, 7L, "writer", "owner-1", SkillVisibility.PUBLIC);

        given(namespaceRepository.findBySlug("team-ai")).willReturn(Optional.of(namespace));
        given(skillBundleRepository.findByNamespaceIdAndSlug(7L, "expert-copywriter")).willReturn(Optional.of(bundle));
        given(skillBundleRepository.save(bundle)).willReturn(bundle);
        given(skillRepository.findByIdIn(List.of(101L, 102L))).willReturn(List.of(planner, writer));

        service.updateDraft(
                "team-ai",
                "expert-copywriter",
                minimalCommand("team-ai", "expert-copywriter", List.of(
                        new SkillBundleService.BundleItemCommand(101L, 0, null),
                        new SkillBundleService.BundleItemCommand(102L, 1, null)
                )),
                "owner-1",
                Map.of(7L, NamespaceRole.OWNER)
        );

        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(skillBundleItemRepository);
        inOrder.verify(skillBundleItemRepository).deleteByBundleId(20L);
        inOrder.verify(skillBundleItemRepository).flush();
        inOrder.verify(skillBundleItemRepository).saveAll(List.of(
                new SkillBundleItem(20L, 101L, 0, null),
                new SkillBundleItem(20L, 102L, 1, null)
        ));
    }

    @Test
    void updateDraft_shouldFlushDeletedLabelsBeforeReinsertingReplacementLabels() {
        Namespace namespace = namespace(7L, "team-ai");
        SkillBundle bundle = bundle(20L, 7L, "expert-copywriter", "owner-1", SkillVisibility.PRIVATE);
        LabelDefinition official = label(501L, "official");

        given(namespaceRepository.findBySlug("team-ai")).willReturn(Optional.of(namespace));
        given(skillBundleRepository.findByNamespaceIdAndSlug(7L, "expert-copywriter")).willReturn(Optional.of(bundle));
        given(skillBundleRepository.save(bundle)).willReturn(bundle);
        given(labelDefinitionRepository.findBySlugIgnoreCase("official")).willReturn(Optional.of(official));

        service.updateDraft(
                "team-ai",
                "expert-copywriter",
                new SkillBundleService.BundleDraftCommand(
                        "team-ai",
                        "Expert Copywriter",
                        "expert-copywriter",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        SkillVisibility.PRIVATE,
                        SkillBundleStatus.DRAFT,
                        List.of("official"),
                        List.of()
                ),
                "owner-1",
                Map.of(7L, NamespaceRole.OWNER)
        );

        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(skillBundleLabelRepository);
        inOrder.verify(skillBundleLabelRepository).deleteByBundleId(20L);
        inOrder.verify(skillBundleLabelRepository).flush();
        inOrder.verify(skillBundleLabelRepository).saveAll(List.of(new SkillBundleLabel(20L, 501L, "owner-1")));
    }

    @Test
    void getDetail_shouldHidePrivateBundleFromNonMembers() {
        Namespace namespace = namespace(7L, "team-ai");
        SkillBundle bundle = bundle(20L, 7L, "expert-copywriter", "owner-1", SkillVisibility.PRIVATE);

        given(namespaceRepository.findBySlug("team-ai")).willReturn(Optional.of(namespace));
        given(skillBundleRepository.findByNamespaceIdAndSlug(7L, "expert-copywriter")).willReturn(Optional.of(bundle));

        assertThatThrownBy(() -> service.getVisibleDetail("team-ai", "expert-copywriter", "viewer-1", Map.of()))
                .isInstanceOf(DomainForbiddenException.class);
    }

    @Test
    void getDetail_shouldReturnOrderedItemsAndLabelsForVisibleBundle() {
        Namespace namespace = namespace(7L, "team-ai");
        SkillBundle bundle = bundle(20L, 7L, "expert-copywriter", "owner-1", SkillVisibility.PUBLIC);
        Skill planner = skill(101L, 7L, "planner", "owner-1", SkillVisibility.PUBLIC);
        planner.setLatestVersionId(1001L);
        planner.setDisplayName("Planner");
        Skill writer = skill(102L, 7L, "writer", "owner-1", SkillVisibility.PUBLIC);
        writer.setLatestVersionId(1002L);
        writer.setDisplayName("Writer");
        LabelDefinition official = label(501L, "official");

        given(namespaceRepository.findBySlug("team-ai")).willReturn(Optional.of(namespace));
        given(skillBundleRepository.findByNamespaceIdAndSlug(7L, "expert-copywriter")).willReturn(Optional.of(bundle));
        given(skillBundleItemRepository.findByBundleIdOrderBySortOrderAscIdAsc(20L)).willReturn(List.of(
                new SkillBundleItem(20L, 102L, 1, "Draft second"),
                new SkillBundleItem(20L, 101L, 0, "Plan first")
        ));
        given(skillRepository.findByIdIn(List.of(101L, 102L))).willReturn(List.of(planner, writer));
        given(skillBundleLabelRepository.findByBundleId(20L)).willReturn(List.of(new SkillBundleLabel(20L, 501L, "owner-1")));
        given(labelDefinitionRepository.findById(501L)).willReturn(Optional.of(official));

        SkillBundleService.BundleDetail detail = service.getVisibleDetail("team-ai", "expert-copywriter", null, Map.of());

        assertThat(detail.bundle().getSlug()).isEqualTo("expert-copywriter");
        assertThat(detail.items()).extracting(SkillBundleService.BundleItemDetail::skillSlug)
                .containsExactly("planner", "writer");
        assertThat(detail.labels()).containsExactly("official");
    }

    @Test
    void createDraft_shouldRejectUnknownSkillItems() {
        Namespace namespace = namespace(7L, "team-ai");
        given(namespaceService.getNamespaceBySlugForRead("team-ai", "owner-1", Map.of(7L, NamespaceRole.OWNER)))
                .willReturn(namespace);
        given(skillBundleRepository.findByNamespaceIdAndSlug(7L, "expert-copywriter")).willReturn(Optional.empty());
        given(skillBundleRepository.save(any(SkillBundle.class))).willAnswer(invocation -> {
            SkillBundle bundle = invocation.getArgument(0);
            setField(bundle, "id", 20L);
            return bundle;
        });
        given(skillRepository.findByIdIn(List.of(404L))).willReturn(List.of());

        assertThatThrownBy(() -> service.createDraft(
                minimalCommand("team-ai", "expert-copywriter", List.of(new SkillBundleService.BundleItemCommand(404L, 0, null))),
                "owner-1",
                Map.of(7L, NamespaceRole.OWNER)))
                .isInstanceOf(DomainBadRequestException.class);
    }

    @Test
    void createDraft_shouldRejectPublishedBundleWithoutItems() {
        Namespace namespace = namespace(7L, "team-ai");
        given(namespaceService.getNamespaceBySlugForRead("team-ai", "owner-1", Map.of(7L, NamespaceRole.OWNER)))
                .willReturn(namespace);
        given(skillBundleRepository.findByNamespaceIdAndSlug(7L, "expert-copywriter")).willReturn(Optional.empty());
        given(skillBundleRepository.save(any(SkillBundle.class))).willAnswer(invocation -> {
            SkillBundle bundle = invocation.getArgument(0);
            setField(bundle, "id", 20L);
            return bundle;
        });

        assertThatThrownBy(() -> service.createDraft(
                command("team-ai", "expert-copywriter", SkillVisibility.PUBLIC, SkillBundleStatus.PUBLISHED, List.of()),
                "owner-1",
                Map.of(7L, NamespaceRole.OWNER)))
                .isInstanceOf(DomainBadRequestException.class)
                .hasMessageContaining("error.skillBundle.published.items.required");
    }

    @Test
    void createDraft_shouldRejectPrivateSkillInPublishedPublicBundle() {
        Namespace namespace = namespace(7L, "team-ai");
        Skill privateSkill = skill(101L, 7L, "private-planner", "owner-1", SkillVisibility.PRIVATE);
        privateSkill.setLatestVersionId(1001L);
        given(namespaceService.getNamespaceBySlugForRead("team-ai", "owner-1", Map.of(7L, NamespaceRole.OWNER)))
                .willReturn(namespace);
        given(skillBundleRepository.findByNamespaceIdAndSlug(7L, "expert-copywriter")).willReturn(Optional.empty());
        given(skillBundleRepository.save(any(SkillBundle.class))).willAnswer(invocation -> {
            SkillBundle bundle = invocation.getArgument(0);
            setField(bundle, "id", 20L);
            return bundle;
        });
        given(skillRepository.findByIdIn(List.of(101L))).willReturn(List.of(privateSkill));

        assertThatThrownBy(() -> service.createDraft(
                command("team-ai", "expert-copywriter", SkillVisibility.PUBLIC, SkillBundleStatus.PUBLISHED,
                        List.of(new SkillBundleService.BundleItemCommand(101L, 0, null))),
                "owner-1",
                Map.of(7L, NamespaceRole.OWNER)))
                .isInstanceOf(DomainBadRequestException.class)
                .hasMessageContaining("error.skillBundle.item.invalid");
    }

    @Test
    void getDetail_shouldNotExposePrivateSkillItemsToAnonymousViewers() {
        Namespace namespace = namespace(7L, "team-ai");
        SkillBundle bundle = bundle(20L, 7L, "expert-copywriter", "owner-1", SkillVisibility.PUBLIC);
        Skill privateSkill = skill(101L, 7L, "private-planner", "owner-1", SkillVisibility.PRIVATE);
        privateSkill.setDisplayName("Private Planner");
        privateSkill.setLatestVersionId(1001L);

        given(namespaceRepository.findBySlug("team-ai")).willReturn(Optional.of(namespace));
        given(skillBundleRepository.findByNamespaceIdAndSlug(7L, "expert-copywriter")).willReturn(Optional.of(bundle));
        given(skillBundleItemRepository.findByBundleIdOrderBySortOrderAscIdAsc(20L)).willReturn(List.of(
                new SkillBundleItem(20L, 101L, 0, "Private note")
        ));
        given(skillRepository.findByIdIn(List.of(101L))).willReturn(List.of(privateSkill));
        given(namespaceRepository.findByIdIn(List.of(7L))).willReturn(List.of(namespace));
        given(skillBundleLabelRepository.findByBundleId(20L)).willReturn(List.of());

        SkillBundleService.BundleDetail detail = service.getVisibleDetail("team-ai", "expert-copywriter", null, Map.of());

        assertThat(detail.items()).isEmpty();
    }

    @Test
    void recordDownload_shouldIncrementDownloadCountWithoutChangingBundleContent() {
        SkillBundle bundle = bundle(20L, 7L, "expert-copywriter", "owner-1", SkillVisibility.PUBLIC);

        service.recordDownload(bundle);

        assertThat(bundle.getDownloadCount()).isEqualTo(1L);
        verify(skillBundleRepository).incrementDownloadCount(20L);
    }

    private SkillBundleService.BundleDraftCommand minimalCommand(String namespaceSlug, String slug) {
        return minimalCommand(namespaceSlug, slug, List.of());
    }

    private SkillBundleService.BundleDraftCommand minimalCommand(
            String namespaceSlug,
            String slug,
            List<SkillBundleService.BundleItemCommand> items) {
        return command(namespaceSlug, slug, SkillVisibility.PRIVATE, SkillBundleStatus.DRAFT, items);
    }

    private SkillBundleService.BundleDraftCommand command(
            String namespaceSlug,
            String slug,
            SkillVisibility visibility,
            SkillBundleStatus status,
            List<SkillBundleService.BundleItemCommand> items) {
        return new SkillBundleService.BundleDraftCommand(
                namespaceSlug,
                "Expert Copywriter",
                slug,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                visibility,
                status,
                List.of(),
                items
        );
    }

    private Namespace namespace(Long id, String slug) {
        Namespace namespace = new Namespace(slug, "Team AI", "owner-1");
        setField(namespace, "id", id);
        return namespace;
    }

    private Skill skill(Long id, Long namespaceId, String slug, String ownerId, SkillVisibility visibility) {
        Skill skill = new Skill(namespaceId, slug, ownerId, visibility);
        setField(skill, "id", id);
        return skill;
    }

    private SkillBundle bundle(Long id, Long namespaceId, String slug, String ownerId, SkillVisibility visibility) {
        SkillBundle bundle = new SkillBundle(namespaceId, "Expert Copywriter", slug, ownerId, visibility);
        setField(bundle, "id", id);
        bundle.setStatus(SkillBundleStatus.PUBLISHED);
        return bundle;
    }

    private LabelDefinition label(Long id, String slug) {
        LabelDefinition label = new LabelDefinition(slug, LabelType.RECOMMENDED, true, 0, "admin");
        setField(label, "id", id);
        return label;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
