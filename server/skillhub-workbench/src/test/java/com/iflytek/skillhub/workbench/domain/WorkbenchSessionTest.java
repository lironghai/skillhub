package com.iflytek.skillhub.workbench.domain;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkbenchSessionTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-30T00:00:00Z"), ZoneOffset.UTC);
    private static final Instant EXPIRES_AT = Instant.parse("2026-08-06T00:00:00Z");

    @Test
    void createSessionUsesSessionIsolationAndNoSourceVersion() {
        WorkbenchSession session = WorkbenchSession.createSkill(
                "user-1", 10L, "new-skill", "1.0.0", "workbench/user-1/s-1", EXPIRES_AT, CLOCK);

        assertThat(session.getMode()).isEqualTo(WorkbenchMode.CREATE_SKILL);
        assertThat(session.getSourceSkillId()).isNull();
        assertThat(session.getSourceVersionId()).isNull();
        assertThat(session.getRuntimeProvider()).isEqualTo(WorkbenchRuntimeProvider.AGENTSCOPE_JAVA);
        assertThat(session.getIsolationScope()).isEqualTo(WorkbenchIsolationScope.SESSION);
        assertThat(session.getStatus()).isEqualTo(WorkbenchSessionStatus.DRAFT);
    }

    @Test
    void updateSessionRequiresConcreteSourceVersion() {
        assertThatThrownBy(() -> WorkbenchSession.updateSkill(
                "user-1", 10L, 100L, null, "existing-skill", "1.0.1",
                "workbench/user-1/s-2", EXPIRES_AT, CLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sourceVersionId");
    }

    @Test
    void statusTransitionsFollowWorkbenchLifecycle() {
        WorkbenchSession session = WorkbenchSession.updateSkill(
                "user-1", 10L, 100L, 200L, "existing-skill", "1.0.1",
                "workbench/user-1/s-3", EXPIRES_AT, CLOCK);

        session.start(CLOCK);
        session.waitForApproval(CLOCK);
        session.resumeFromApproval(CLOCK);
        session.markReadyForReview(CLOCK);
        session.startPublishing(CLOCK);
        session.markPublished(CLOCK);

        assertThat(session.getStatus()).isEqualTo(WorkbenchSessionStatus.PUBLISHED);
        assertThat(session.isTerminal()).isTrue();
    }

    @Test
    void terminalSessionRejectsFurtherTransitions() {
        WorkbenchSession session = WorkbenchSession.createSkill(
                "user-1", 10L, "new-skill", "1.0.0", "workbench/user-1/s-4", EXPIRES_AT, CLOCK);

        session.cancel(CLOCK);

        assertThatThrownBy(() -> session.start(CLOCK))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CANCELLED");
    }
}
