package com.iflytek.skillhub.workbench.adapter;

import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.workbench.application.CreateWorkbenchSessionCommand;
import com.iflytek.skillhub.workbench.domain.WorkbenchMode;
import com.iflytek.skillhub.workbench.domain.WorkbenchSession;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkbenchAuthorizationAdapterTest {

    private NamespaceMemberRepository namespaceMemberRepository;
    private SkillRepository skillRepository;
    private SkillVersionRepository skillVersionRepository;
    private RbacService rbacService;
    private WorkbenchAuthorizationAdapter adapter;

    @BeforeEach
    void setUp() {
        namespaceMemberRepository = mock(NamespaceMemberRepository.class);
        skillRepository = mock(SkillRepository.class);
        skillVersionRepository = mock(SkillVersionRepository.class);
        rbacService = mock(RbacService.class);
        adapter = new WorkbenchAuthorizationAdapter(
                namespaceMemberRepository,
                skillRepository,
                skillVersionRepository,
                rbacService);
    }

    @Test
    void createSessionRejectsNonMember() {
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(10L, "user-1"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.assertCanCreateSession("user-1", 10L))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void accessSessionRejectsNonOwner() {
        WorkbenchSession session = session("owner-1", 10L, 99L, 101L);

        assertThatThrownBy(() -> adapter.assertCanAccessSession("user-1", session))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void importRejectsUnreadableSourceSkill() {
        WorkbenchSession session = session("user-1", 10L, 99L, 101L);
        Skill sourceSkill = privateSkill(99L, 11L, "owner-2");
        SkillVersion sourceVersion = version(101L, 99L, SkillVersionStatus.PUBLISHED, true);
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(10L, "user-1"))
                .thenReturn(Optional.of(new NamespaceMember(10L, "user-1", NamespaceRole.MEMBER)));
        when(skillRepository.findById(99L)).thenReturn(Optional.of(sourceSkill));
        when(skillVersionRepository.findById(101L)).thenReturn(Optional.of(sourceVersion));
        when(namespaceMemberRepository.findByUserId("user-1"))
                .thenReturn(List.of(new NamespaceMember(10L, "user-1", NamespaceRole.MEMBER)));
        when(rbacService.getUserRoleCodes("user-1")).thenReturn(Set.of("USER"));

        assertThatThrownBy(() -> adapter.assertCanImportSourceVersion("user-1", session))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void importAllowsReadableSourceSkill() {
        WorkbenchSession session = session("user-1", 10L, 99L, 101L);
        Skill sourceSkill = privateSkill(99L, 11L, "owner-2");
        SkillVersion sourceVersion = version(101L, 99L, SkillVersionStatus.PUBLISHED, true);
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(10L, "user-1"))
                .thenReturn(Optional.of(new NamespaceMember(10L, "user-1", NamespaceRole.MEMBER)));
        when(skillRepository.findById(99L)).thenReturn(Optional.of(sourceSkill));
        when(skillVersionRepository.findById(101L)).thenReturn(Optional.of(sourceVersion));
        when(namespaceMemberRepository.findByUserId("user-1"))
                .thenReturn(List.of(
                        new NamespaceMember(10L, "user-1", NamespaceRole.MEMBER),
                        new NamespaceMember(11L, "user-1", NamespaceRole.ADMIN)));
        when(rbacService.getUserRoleCodes("user-1")).thenReturn(Set.of("USER"));

        assertThatCode(() -> adapter.assertCanImportSourceVersion("user-1", session))
                .doesNotThrowAnyException();
    }

    @Test
    void importRejectsPendingReviewSourceVersionForPlainReadableSkillMember() {
        WorkbenchSession session = session("user-1", 10L, 99L, 101L);
        Skill sourceSkill = publicSkill(99L, 11L, "owner-2");
        SkillVersion sourceVersion = version(101L, 99L, SkillVersionStatus.PENDING_REVIEW, false);
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(10L, "user-1"))
                .thenReturn(Optional.of(new NamespaceMember(10L, "user-1", NamespaceRole.MEMBER)));
        when(skillRepository.findById(99L)).thenReturn(Optional.of(sourceSkill));
        when(skillVersionRepository.findById(101L)).thenReturn(Optional.of(sourceVersion));
        when(namespaceMemberRepository.findByUserId("user-1"))
                .thenReturn(List.of(new NamespaceMember(10L, "user-1", NamespaceRole.MEMBER)));
        when(rbacService.getUserRoleCodes("user-1")).thenReturn(Set.of("USER"));

        assertThatThrownBy(() -> adapter.assertCanImportSourceVersion("user-1", session))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("source version");
    }

    @Test
    void createUpdateSessionRejectsUnreadableSourceBeforeDraftIsSaved() {
        CreateWorkbenchSessionCommand command = new CreateWorkbenchSessionCommand(
                "user-1",
                10L,
                WorkbenchMode.UPDATE_SKILL,
                99L,
                101L,
                "target",
                "1.1.0",
                Instant.parse("2026-08-01T00:00:00Z"));
        Skill sourceSkill = publicSkill(99L, 11L, "owner-2");
        SkillVersion sourceVersion = version(101L, 99L, SkillVersionStatus.UPLOADED, false);
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(10L, "user-1"))
                .thenReturn(Optional.of(new NamespaceMember(10L, "user-1", NamespaceRole.MEMBER)));
        when(skillRepository.findById(99L)).thenReturn(Optional.of(sourceSkill));
        when(skillVersionRepository.findById(101L)).thenReturn(Optional.of(sourceVersion));
        when(namespaceMemberRepository.findByUserId("user-1"))
                .thenReturn(List.of(new NamespaceMember(10L, "user-1", NamespaceRole.MEMBER)));
        when(rbacService.getUserRoleCodes("user-1")).thenReturn(Set.of("USER"));

        assertThatThrownBy(() -> adapter.assertCanCreateSession(command))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("source version");
    }

    private WorkbenchSession session(String userId, Long namespaceId, Long sourceSkillId, Long sourceVersionId) {
        WorkbenchSession session = WorkbenchSession.updateSkill(
                userId,
                namespaceId,
                sourceSkillId,
                sourceVersionId,
                "target",
                "1.0.0",
                "workbench/user/session",
                Instant.parse("2026-08-01T00:00:00Z"),
                Clock.fixed(Instant.parse("2026-07-31T00:00:00Z"), ZoneOffset.UTC));
        ReflectionTestUtils.setField(session, "id", 7L);
        return session;
    }

    private Skill privateSkill(Long id, Long namespaceId, String ownerId) {
        Skill skill = new Skill(namespaceId, "source", ownerId, SkillVisibility.PRIVATE);
        skill.setLatestVersionId(101L);
        ReflectionTestUtils.setField(skill, "id", id);
        return skill;
    }

    private Skill publicSkill(Long id, Long namespaceId, String ownerId) {
        Skill skill = new Skill(namespaceId, "source", ownerId, SkillVisibility.PUBLIC);
        skill.setLatestVersionId(101L);
        ReflectionTestUtils.setField(skill, "id", id);
        return skill;
    }

    private SkillVersion version(Long id, Long skillId, SkillVersionStatus status, boolean downloadReady) {
        SkillVersion version = new SkillVersion(skillId, "1.0.0", "owner-2");
        version.setStatus(status);
        version.setDownloadReady(downloadReady);
        ReflectionTestUtils.setField(version, "id", id);
        return version;
    }
}
