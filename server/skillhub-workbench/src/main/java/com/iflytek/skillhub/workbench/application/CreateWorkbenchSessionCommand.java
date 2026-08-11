package com.iflytek.skillhub.workbench.application;

import com.iflytek.skillhub.workbench.domain.WorkbenchMode;
import com.iflytek.skillhub.workbench.domain.WorkbenchSession;
import java.time.Instant;
import java.util.Objects;

public record CreateWorkbenchSessionCommand(
        String userId,
        Long namespaceId,
        WorkbenchMode mode,
        Long sourceSkillId,
        Long sourceVersionId,
        String targetSlug,
        String targetVersion,
        Instant expiresAt
) {
    public CreateWorkbenchSessionCommand {
        WorkbenchSession.requireText(userId, "userId");
        WorkbenchSession.requirePositive(namespaceId, "namespaceId");
        Objects.requireNonNull(mode, "mode");
        WorkbenchSession.requireText(targetSlug, "targetSlug");
        WorkbenchSession.requireText(targetVersion, "targetVersion");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }
}
