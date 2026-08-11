package com.iflytek.skillhub.workbench.poc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionWorkspaceFilePolicyTest {

    @TempDir
    Path tempDir;

    @Test
    void readsAndWritesOnlyInsideSessionWorkspace() {
        SessionWorkspace workspace = new SessionWorkspace("user-a", "session-a", tempDir.resolve("session-a"));
        SessionWorkspaceFilePolicy policy = new SessionWorkspaceFilePolicy(workspace.root());

        policy.write("SKILL.md", "name: demo");

        assertThat(policy.read("SKILL.md")).isEqualTo("name: demo");
        assertThat(policy.resolve("nested/example.md").toString())
                .startsWith(workspace.root().toAbsolutePath().normalize().toString());
    }

    @Test
    void rejectsTraversalAndAbsolutePaths() {
        SessionWorkspace workspace = new SessionWorkspace("user-a", "session-a", tempDir.resolve("session-a"));
        SessionWorkspaceFilePolicy policy = new SessionWorkspaceFilePolicy(workspace.root());

        assertThatThrownBy(() -> policy.read("../other/SKILL.md"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("session workspace");
        assertThatThrownBy(() -> policy.write(tempDir.resolve("outside.md").toString(), "nope"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Absolute paths");
        assertThatThrownBy(() -> policy.write("../session-b/SKILL.md", "nope"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("session workspace");
    }

    @Test
    void rejectsSymbolicLinkSegmentsBeforeReadOrWrite() throws IOException {
        Path workspaceRoot = tempDir.resolve("session-a");
        Path outsideRoot = tempDir.resolve("outside");
        Files.createDirectories(workspaceRoot);
        Files.createDirectories(outsideRoot);
        Files.writeString(outsideRoot.resolve("SKILL.md"), "outside");
        Files.createSymbolicLink(workspaceRoot.resolve("linked"), outsideRoot);

        SessionWorkspace workspace = new SessionWorkspace("user-a", "session-a", workspaceRoot);
        SessionWorkspaceFilePolicy policy = new SessionWorkspaceFilePolicy(workspace.root());

        assertThatThrownBy(() -> policy.read("linked/SKILL.md"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Symbolic links");
        assertThatThrownBy(() -> policy.write("linked/NEW.md", "nope"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Symbolic links");
    }
}
