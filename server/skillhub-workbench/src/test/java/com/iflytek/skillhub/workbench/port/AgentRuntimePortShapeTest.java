package com.iflytek.skillhub.workbench.port;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentRuntimePortShapeTest {

    @Test
    void commandRequiresSessionScopedIdentityAndWorkspace() {
        AgentRuntimeRunCommand command = new AgentRuntimeRunCommand(
                "user-1",
                7L,
                "workbench/user/session",
                "update SKILL.md");

        assertThat(command.userId()).isEqualTo("user-1");
        assertThat(command.sessionId()).isEqualTo(7L);
        assertThat(command.workspaceKey()).isEqualTo("workbench/user/session");
        assertThat(command.sessionContext()).isEmpty();
    }

    @Test
    void commandCanCarryWorkbenchSessionContext() {
        AgentRuntimeRunCommand command = new AgentRuntimeRunCommand(
                "user-1",
                7L,
                "workbench/user/session",
                "目标技能标识: report-helper",
                "update SKILL.md");

        assertThat(command.sessionContext()).isEqualTo("目标技能标识: report-helper");
    }

    @Test
    void commandRejectsBlankMessage() {
        assertThatThrownBy(() -> new AgentRuntimeRunCommand(
                "user-1",
                7L,
                "workbench/user/session",
                " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("message");
    }

    @Test
    void fakeRuntimeCanStartAndCancelRun() {
        AgentRuntimePort runtime = new FakeRuntime();

        AgentRuntimeRunHandle started = runtime.start(new AgentRuntimeRunCommand(
                "user-1",
                7L,
                "workbench/user/session",
                "update SKILL.md"));
        AgentRuntimeRunHandle cancelled = runtime.cancel(new AgentRuntimeCancelCommand(
                "user-1",
                7L,
                started.runId()));

        assertThat(started.status()).isEqualTo(AgentRuntimeRunStatus.STARTED);
        assertThat(cancelled.status()).isEqualTo(AgentRuntimeRunStatus.CANCELLED);
    }

    private static final class FakeRuntime implements AgentRuntimePort {
        @Override
        public AgentRuntimeRunHandle start(AgentRuntimeRunCommand command) {
            return new AgentRuntimeRunHandle(
                    command.sessionId() + "-run",
                    AgentRuntimeRunStatus.STARTED,
                    "accepted");
        }

        @Override
        public AgentRuntimeRunHandle cancel(AgentRuntimeCancelCommand command) {
            return new AgentRuntimeRunHandle(
                    command.runId(),
                    AgentRuntimeRunStatus.CANCELLED,
                    "cancelled");
        }
    }
}
