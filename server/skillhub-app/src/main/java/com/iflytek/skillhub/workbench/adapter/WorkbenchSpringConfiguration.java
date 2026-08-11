package com.iflytek.skillhub.workbench.adapter;

import com.iflytek.skillhub.workbench.application.WorkbenchSessionApplicationService;
import com.iflytek.skillhub.workbench.port.AgentRuntimePort;
import com.iflytek.skillhub.workbench.port.WorkbenchAuthorizationPort;
import com.iflytek.skillhub.workbench.port.WorkbenchFileSnapshotRepository;
import com.iflytek.skillhub.workbench.port.WorkbenchMcpBindingRepository;
import com.iflytek.skillhub.workbench.port.WorkbenchMcpCatalogPort;
import com.iflytek.skillhub.workbench.port.WorkbenchPublishCandidateRepository;
import com.iflytek.skillhub.workbench.port.WorkbenchSessionEventRepository;
import com.iflytek.skillhub.workbench.port.WorkbenchSessionRepository;
import com.iflytek.skillhub.workbench.port.WorkbenchSkillPublishPort;
import com.iflytek.skillhub.workbench.port.WorkbenchSkillSourcePort;
import com.iflytek.skillhub.workbench.port.WorkbenchToolApprovalRepository;
import com.iflytek.skillhub.workbench.port.WorkbenchWorkspaceStoragePort;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WorkbenchSpringConfiguration {

    @Bean
    WorkbenchSessionApplicationService workbenchSessionApplicationService(
            WorkbenchSessionRepository sessionRepository,
            WorkbenchSessionEventRepository eventRepository,
            WorkbenchFileSnapshotRepository fileSnapshotRepository,
            WorkbenchMcpBindingRepository mcpBindingRepository,
            WorkbenchMcpCatalogPort mcpCatalogPort,
            WorkbenchToolApprovalRepository toolApprovalRepository,
            WorkbenchPublishCandidateRepository publishCandidateRepository,
            WorkbenchSkillPublishPort skillPublishPort,
            WorkbenchSkillSourcePort skillSourcePort,
            WorkbenchWorkspaceStoragePort workspaceStoragePort,
            WorkbenchAuthorizationPort authorizationPort,
            AgentRuntimePort runtimePort,
            Clock clock) {
        return new WorkbenchSessionApplicationService(
                sessionRepository,
                eventRepository,
                fileSnapshotRepository,
                mcpBindingRepository,
                mcpCatalogPort,
                toolApprovalRepository,
                publishCandidateRepository,
                skillPublishPort,
                skillSourcePort,
                workspaceStoragePort,
                authorizationPort,
                runtimePort,
                clock);
    }
}
