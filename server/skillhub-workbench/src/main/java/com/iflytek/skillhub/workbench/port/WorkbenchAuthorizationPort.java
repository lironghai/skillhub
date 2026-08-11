package com.iflytek.skillhub.workbench.port;

import com.iflytek.skillhub.workbench.application.CreateWorkbenchSessionCommand;
import com.iflytek.skillhub.workbench.domain.WorkbenchSession;

public interface WorkbenchAuthorizationPort {
    void assertCanCreateSession(String userId, Long namespaceId);

    default void assertCanCreateSession(CreateWorkbenchSessionCommand command) {
        assertCanCreateSession(command.userId(), command.namespaceId());
    }

    void assertCanAccessSession(String userId, WorkbenchSession session);

    void assertCanImportSourceVersion(String userId, WorkbenchSession session);
}
