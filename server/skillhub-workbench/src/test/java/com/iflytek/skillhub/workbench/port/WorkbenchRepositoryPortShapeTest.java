package com.iflytek.skillhub.workbench.port;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkbenchRepositoryPortShapeTest {

    private static final List<Class<?>> PORTS = List.of(
            WorkbenchSessionRepository.class,
            WorkbenchSessionEventRepository.class,
            WorkbenchFileSnapshotRepository.class,
            WorkbenchMcpBindingRepository.class,
            WorkbenchToolApprovalRepository.class,
            WorkbenchPublishCandidateRepository.class,
            WorkbenchMcpCatalogPort.class,
            WorkbenchAuthorizationPort.class,
            WorkbenchSkillSourcePort.class,
            WorkbenchWorkspaceStoragePort.class,
            WorkbenchSkillPublishPort.class
    );

    @Test
    void repositoryPortsRemainFrameworkLightInterfaces() {
        for (Class<?> port : PORTS) {
            assertThat(port.isInterface()).as(port.getSimpleName()).isTrue();
            assertThat(port.getAnnotations()).as(port.getSimpleName()).isEmpty();
            for (Method method : port.getDeclaredMethods()) {
                assertThat(method.getReturnType().getName())
                        .as(port.getSimpleName() + "." + method.getName())
                        .doesNotStartWith("org.springframework");
                for (Annotation annotation : method.getAnnotations()) {
                    assertThat(annotation.annotationType().getName())
                            .as(port.getSimpleName() + "." + method.getName())
                            .doesNotStartWith("org.springframework");
                }
            }
        }
    }
}
