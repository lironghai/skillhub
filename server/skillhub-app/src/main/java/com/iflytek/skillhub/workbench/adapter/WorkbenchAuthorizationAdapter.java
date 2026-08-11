package com.iflytek.skillhub.workbench.adapter;

import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillInstallability;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.VisibilityChecker;
import com.iflytek.skillhub.workbench.application.CreateWorkbenchSessionCommand;
import com.iflytek.skillhub.workbench.domain.WorkbenchMode;
import com.iflytek.skillhub.workbench.domain.WorkbenchSession;
import com.iflytek.skillhub.workbench.port.WorkbenchAuthorizationPort;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class WorkbenchAuthorizationAdapter implements WorkbenchAuthorizationPort {

    private final NamespaceMemberRepository namespaceMemberRepository;
    private final SkillRepository skillRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final RbacService rbacService;
    private final VisibilityChecker visibilityChecker = new VisibilityChecker();

    public WorkbenchAuthorizationAdapter(
            NamespaceMemberRepository namespaceMemberRepository,
            SkillRepository skillRepository,
            SkillVersionRepository skillVersionRepository,
            RbacService rbacService) {
        this.namespaceMemberRepository = namespaceMemberRepository;
        this.skillRepository = skillRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.rbacService = rbacService;
    }

    @Override
    public void assertCanCreateSession(String userId, Long namespaceId) {
        namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, userId)
                .orElseThrow(() -> new SecurityException("user is not a member of namespace " + namespaceId));
    }

    @Override
    public void assertCanCreateSession(CreateWorkbenchSessionCommand command) {
        assertCanCreateSession(command.userId(), command.namespaceId());
        if (command.mode() != WorkbenchMode.UPDATE_SKILL) {
            return;
        }
        assertCanUseSourceVersion(command.userId(), command.sourceSkillId(), command.sourceVersionId());
    }

    @Override
    public void assertCanAccessSession(String userId, WorkbenchSession session) {
        if (!session.getUserId().equals(userId)) {
            throw new SecurityException("user cannot access this workbench session");
        }
    }

    @Override
    public void assertCanImportSourceVersion(String userId, WorkbenchSession session) {
        assertCanAccessSession(userId, session);
        assertCanCreateSession(userId, session.getNamespaceId());
        assertCanUseSourceVersion(userId, session.getSourceSkillId(), session.getSourceVersionId());
    }

    private void assertCanUseSourceVersion(String userId, Long sourceSkillId, Long sourceVersionId) {
        Skill sourceSkill = skillRepository.findById(sourceSkillId)
                .orElseThrow(() -> new SecurityException("source skill is unavailable"));
        SkillVersion sourceVersion = skillVersionRepository.findById(sourceVersionId)
                .orElseThrow(() -> new SecurityException("source version is unavailable"));
        if (!sourceVersion.getSkillId().equals(sourceSkill.getId())) {
            throw new SecurityException("source version does not belong to source skill");
        }
        Map<Long, NamespaceRole> namespaceRoles = namespaceMemberRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(
                        NamespaceMember::getNamespaceId,
                        NamespaceMember::getRole,
                        (left, right) -> left));
        Set<String> platformRoles = rbacService.getUserRoleCodes(userId);
        if (!visibilityChecker.canAccess(sourceSkill, userId, namespaceRoles, platformRoles)) {
            throw new SecurityException("user cannot read source skill");
        }
        if (!canUseVersion(sourceSkill, sourceVersion, userId, namespaceRoles, platformRoles)) {
            throw new SecurityException("user cannot read source version");
        }
    }

    private boolean canUseVersion(Skill skill,
                                  SkillVersion version,
                                  String userId,
                                  Map<Long, NamespaceRole> namespaceRoles,
                                  Set<String> platformRoles) {
        if (isSuperAdmin(platformRoles)) {
            return true;
        }
        if (version.getStatus() == SkillVersionStatus.PUBLISHED) {
            return SkillInstallability.isInstallableVersion(version);
        }
        return canManageSourceDraft(skill, userId, namespaceRoles);
    }

    private boolean canManageSourceDraft(Skill skill, String userId, Map<Long, NamespaceRole> namespaceRoles) {
        if (userId == null) {
            return false;
        }
        if (skill.getOwnerId().equals(userId)) {
            return true;
        }
        NamespaceRole role = namespaceRoles == null ? null : namespaceRoles.get(skill.getNamespaceId());
        return role == NamespaceRole.OWNER || role == NamespaceRole.ADMIN;
    }

    private boolean isSuperAdmin(Set<String> platformRoles) {
        return platformRoles != null && platformRoles.contains("SUPER_ADMIN");
    }
}
