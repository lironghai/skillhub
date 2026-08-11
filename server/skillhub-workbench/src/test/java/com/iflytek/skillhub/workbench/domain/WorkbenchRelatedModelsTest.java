package com.iflytek.skillhub.workbench.domain;

import com.iflytek.skillhub.domain.skill.SkillVisibility;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkbenchRelatedModelsTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-30T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void fileSnapshotRejectsWorkspaceEscapes() {
        assertThatThrownBy(() -> new WorkbenchFileSnapshot(
                1L, WorkbenchFileSnapshotType.BASELINE, "../SKILL.md",
                "snapshots/1/SKILL.md", "a".repeat(64), 100L,
                "text/markdown", null, CLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("relative workspace path");
    }

    @Test
    void fileSnapshotRejectsNonNormalizedPathSegments() {
        String[] invalidPaths = {
                "",
                "/SKILL.md",
                "C:/workspace/SKILL.md",
                "dir//SKILL.md",
                ".",
                "./SKILL.md",
                "dir/.",
                "dir/../SKILL.md",
                "dir\\..\\SKILL.md"
        };

        for (String invalidPath : invalidPaths) {
            assertThatThrownBy(() -> new WorkbenchFileSnapshot(
                    1L, WorkbenchFileSnapshotType.BASELINE, invalidPath,
                    "snapshots/1/SKILL.md", "a".repeat(64), 100L,
                    "text/markdown", null, CLOCK))
                    .as(invalidPath)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("relative workspace path");
        }
    }

    @Test
    void approvalCanBeDecidedOnlyOnce() {
        WorkbenchToolApproval approval = new WorkbenchToolApproval(
                1L, 10L, "deploy", "context-forge", WorkbenchToolRiskLevel.MUTATING,
                "{\"target\":\"redacted\"}", CLOCK);

        approval.approve("user-1", CLOCK);

        assertThat(approval.getStatus()).isEqualTo(WorkbenchToolApprovalStatus.APPROVED);
        assertThat(approval.getDecisionBy()).isEqualTo("user-1");
        assertThatThrownBy(() -> approval.reject("user-1", CLOCK))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already decided");
    }

    @Test
    void deniedApprovalIsBlockedImmediatelyAndCannotBeApproved() {
        WorkbenchToolApproval approval = new WorkbenchToolApproval(
                1L, 10L, "host-shell", "context-forge", WorkbenchToolRiskLevel.DENIED,
                "{\"command\":\"redacted\"}", CLOCK);

        assertThat(approval.getStatus()).isEqualTo(WorkbenchToolApprovalStatus.REJECTED);
        assertThat(approval.getDecisionAt()).isEqualTo(CLOCK.instant());
        assertThatThrownBy(() -> approval.approve("user-1", CLOCK))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("denied");
    }

    @Test
    void publishCandidateRequiresPassedValidationBeforePublish() {
        WorkbenchPublishCandidate candidate = new WorkbenchPublishCandidate(
                1L, "sha256:abc", 2, 256L, SkillVisibility.PRIVATE, null, CLOCK);

        assertThatThrownBy(() -> candidate.markPublished(100L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("validated");

        candidate.markValidationPassed("{\"includedFiles\":[\"SKILL.md\"]}");
        candidate.markPublished(100L);

        assertThat(candidate.getValidationStatus()).isEqualTo(WorkbenchPublishValidationStatus.PUBLISHED);
        assertThat(candidate.getSkillVersionId()).isEqualTo(100L);
    }

    @Test
    void publishedCandidateCannotReturnToValidationStates() {
        WorkbenchPublishCandidate candidate = new WorkbenchPublishCandidate(
                1L, "sha256:abc", 2, 256L, SkillVisibility.PUBLIC, "{}", CLOCK);

        candidate.markValidationPassed("{\"includedFiles\":[\"SKILL.md\"]}");
        candidate.markPublished(100L);

        assertThatThrownBy(() -> candidate.markValidationPassed("{}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("published");
        assertThatThrownBy(() -> candidate.markValidationFailed("{}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("published");
        assertThat(candidate.getValidationStatus()).isEqualTo(WorkbenchPublishValidationStatus.PUBLISHED);
        assertThat(candidate.getVisibility()).isEqualTo(SkillVisibility.PUBLIC);
        assertThat(candidate.getSkillVersionId()).isEqualTo(100L);
    }

    @Test
    void mcpBindingDefaultsJsonCollectionsAndCanBeDisabled() {
        WorkbenchMcpBinding binding = new WorkbenchMcpBinding(
                1L, "server-1", WorkbenchMcpCatalogSource.CONTEXT_FORGE,
                "mcp://runtime/server-1", null, null, null, "v1", CLOCK);

        binding.disable();

        assertThat(binding.getEnabledToolsJson()).isEqualTo("[]");
        assertThat(binding.getDisabledToolsJson()).isEqualTo("[]");
        assertThat(binding.getToolPolicyJson()).isEqualTo("{}");
        assertThat(binding.getStatus()).isEqualTo(WorkbenchMcpBindingStatus.DISABLED);
    }

    @Test
    void jsonFieldsRejectInvalidJsonAndWrongContainerShapes() {
        assertThatThrownBy(() -> new WorkbenchSessionEvent(
                1L, WorkbenchSessionEventType.MODEL_MESSAGE, "[\"not-object\"]", CLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JSON object");

        assertThatThrownBy(() -> new WorkbenchSessionEvent(
                1L, WorkbenchSessionEventType.MODEL_MESSAGE, "{bad", CLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valid JSON");

        assertThatThrownBy(() -> new WorkbenchMcpBinding(
                1L, "server-1", WorkbenchMcpCatalogSource.CONTEXT_FORGE,
                "mcp://runtime/server-1", "{}", null, "{}", "v1", CLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JSON array");

        assertThatThrownBy(() -> new WorkbenchMcpBinding(
                1L, "server-1", WorkbenchMcpCatalogSource.CONTEXT_FORGE,
                "mcp://runtime/server-1", "[]", "[]", "[]", "v1", CLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JSON object");

        assertThatThrownBy(() -> new WorkbenchToolApproval(
                1L, 10L, "deploy", "context-forge", WorkbenchToolRiskLevel.MUTATING,
                "[]", CLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JSON object");

        assertThatThrownBy(() -> new WorkbenchPublishCandidate(
                1L, "sha256:abc", 2, 256L, SkillVisibility.PRIVATE, "[]", CLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JSON object");
    }
}
