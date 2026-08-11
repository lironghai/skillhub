package com.iflytek.skillhub.workbench.domain;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkbenchSessionSourceVersionTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-30T00:00:00Z"), ZoneOffset.UTC);
    private static final Instant EXPIRES_AT = Instant.parse("2026-08-06T00:00:00Z");

    @Test
    void sourceVersionValueObjectKeepsUpdateSourceIdsTogether() {
        WorkbenchSourceVersion sourceVersion = new WorkbenchSourceVersion(100L, 200L);

        WorkbenchSession session = WorkbenchSession.updateSkill(
                "user-1", 10L, sourceVersion, "existing-skill", "1.0.1",
                "workbench/user-1/s-3", EXPIRES_AT, CLOCK);

        assertThat(session.getSourceSkillId()).isEqualTo(100L);
        assertThat(session.getSourceVersionId()).isEqualTo(200L);
    }
}
