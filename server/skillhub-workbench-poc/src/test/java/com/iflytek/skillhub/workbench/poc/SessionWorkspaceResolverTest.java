package com.iflytek.skillhub.workbench.poc;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionWorkspaceResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void resolvesConcurrentSessionsToDifferentWorkspaceRoots() throws Exception {
        SessionWorkspaceResolver resolver = new SessionWorkspaceResolver(tempDir);
        Callable<SessionWorkspace> first = () -> resolver.resolve("user-a", "session-1");
        Callable<SessionWorkspace> second = () -> resolver.resolve("user-a", "session-2");

        try (var executor = Executors.newFixedThreadPool(2)) {
            List<SessionWorkspace> workspaces = executor.invokeAll(List.of(first, second)).stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception ex) {
                            throw new IllegalStateException(ex);
                        }
                    })
                    .toList();

            assertThat(workspaces.get(0).root()).isNotEqualTo(workspaces.get(1).root());
            assertThat(workspaces)
                    .allSatisfy(workspace -> assertThat(workspace.root()).startsWith(tempDir.toAbsolutePath().normalize()));
        }
    }
}
