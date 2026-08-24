package com.devmind.module.ai.controller;

import com.devmind.common.security.AuthenticatedUser;
import com.devmind.module.ai.agent.ApprovalDecision;
import com.devmind.module.ai.agent.DeepSeekAgentModelClient;
import com.devmind.module.ai.dto.ProposalDecisionRequest;
import com.devmind.module.ai.dto.WorkflowKeyRequest;
import com.devmind.module.ai.service.ProposalApprovalCommand;
import com.devmind.module.ai.service.RepairWorkflowActionService;
import com.devmind.module.ai.service.RepairWorkflowQueryService;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RepairWorkflowControllerTest {

    @Test
    void actionsShouldAlwaysUseTheAuthenticatedUser() {
        RepairWorkflowQueryService queryService = mock(RepairWorkflowQueryService.class);
        RepairWorkflowActionService actionService = mock(RepairWorkflowActionService.class);
        DeepSeekAgentModelClient modelClient = mock(DeepSeekAgentModelClient.class);
        RepairWorkflowController controller = new RepairWorkflowController(
                queryService, actionService, modelClient);
        AuthenticatedUser user = new AuthenticatedUser(7L, "alice");

        controller.triage(user, 11L, new WorkflowKeyRequest("triage-1"));
        controller.review(user, 21L, new WorkflowKeyRequest("review-1"));
        controller.decide(user, 21L, new ProposalDecisionRequest(
                ApprovalDecision.APPROVE_WITH_EDIT,
                "approval-1",
                "{\"tags\":\"spring,transaction\"}",
                "Narrowed by the owner."));
        controller.execute(user, 21L, new WorkflowKeyRequest("execute-1"));

        verify(actionService).triage(7L, 11L, "triage-1", modelClient);
        verify(actionService).review(7L, 21L, "review-1", modelClient);
        verify(actionService).decide(eq(7L), eq(21L), any(ProposalApprovalCommand.class));
        verify(actionService).execute(7L, 21L, "execute-1");
    }
}
