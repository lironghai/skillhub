package com.iflytek.skillhub.workbench.poc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class MockMcpApprovalPolicyTest {

    @Test
    void mutatingMcpToolProducesApprovalRequiredEventBeforeExecution() {
        MockMcpApprovalPolicy policy = new MockMcpApprovalPolicy();
        MockMcpToolRequest request = new MockMcpToolRequest(
                "server-a",
                "delete_skill",
                ToolRisk.MUTATING,
                Map.of("skillId", "skill-1"));

        WorkbenchApprovalEvent event = policy.evaluate(request);

        assertThat(event.type()).isEqualTo(WorkbenchApprovalEvent.Type.APPROVAL_REQUIRED);
        assertThat(event.executed()).isFalse();
        assertThat(event.agentScopeEvent().getToolCalls())
                .singleElement()
                .satisfies(toolCall -> {
                    assertThat(toolCall.getName()).isEqualTo("delete_skill");
                    assertThat(toolCall.getInput()).containsEntry("skillId", "skill-1");
                });
    }

    @Test
    void readOnlyMcpToolDoesNotRequireApproval() {
        MockMcpApprovalPolicy policy = new MockMcpApprovalPolicy();
        MockMcpToolRequest request = new MockMcpToolRequest(
                "server-a",
                "list_skills",
                ToolRisk.READ_ONLY,
                Map.of());

        WorkbenchApprovalEvent event = policy.evaluate(request);

        assertThat(event.type()).isEqualTo(WorkbenchApprovalEvent.Type.ALLOWED);
        assertThat(event.executed()).isFalse();
    }

    @Test
    void unknownMcpToolRequiresApprovalBeforeExecution() {
        MockMcpApprovalPolicy policy = new MockMcpApprovalPolicy();
        MockMcpToolRequest request = new MockMcpToolRequest(
                "server-a",
                "experimental_tool",
                ToolRisk.UNKNOWN,
                Map.of("path", "SKILL.md"));

        WorkbenchApprovalEvent event = policy.evaluate(request);

        assertThat(event.type()).isEqualTo(WorkbenchApprovalEvent.Type.APPROVAL_REQUIRED);
        assertThat(event.executed()).isFalse();
        assertThat(event.agentScopeEvent().getToolCalls())
                .singleElement()
                .satisfies(toolCall -> assertThat(toolCall.getName()).isEqualTo("experimental_tool"));
    }
}
