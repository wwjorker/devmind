package com.devmind.module.ai.service;

import com.devmind.common.exception.BizException;
import com.devmind.module.ai.agent.AgentModelClient;
import com.devmind.module.ai.agent.AgentRunStatus;
import com.devmind.module.ai.agent.RepairProposalStatus;
import com.devmind.module.ai.entity.AgentRun;
import com.devmind.module.ai.entity.RepairProposal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepairWorkflowActionServiceTest {

    @Mock private AgentOrchestrator orchestrator;
    @Mock private AgentRunPersistenceService runService;
    @Mock private RepairProposalService proposalService;
    @Mock private ProposalReviewService reviewService;
    @Mock private ProposalApprovalService approvalService;
    @Mock private RepairExecutor repairExecutor;
    @Mock private AgentModelClient modelClient;

    private RepairWorkflowActionService service;

    @BeforeEach
    void setUp() {
        service = new RepairWorkflowActionService(
                orchestrator, runService, proposalService,
                reviewService, approvalService, repairExecutor);
    }

    @Test
    void shouldRejectANonDraftProposalWithoutCreatingAnOrphanRun() {
        RepairProposal proposal = proposal(RepairProposalStatus.REJECTED);
        when(proposalService.getOwned(7L, 31L)).thenReturn(proposal);

        assertThatThrownBy(() -> service.review(7L, 31L, "review-new", modelClient))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("not awaiting review");

        verify(runService, never()).startRun(any(), any(), any(), any(), any());
        verify(reviewService, never()).review(any(), any(), any(), any());
    }

    @Test
    void shouldReturnTheStoredResultForASucceededReviewRetry() {
        RepairProposal proposal = proposal(RepairProposalStatus.AWAITING_APPROVAL);
        AgentRun run = new AgentRun();
        run.setBadCaseId(41L);
        run.setStatus(AgentRunStatus.SUCCEEDED.name());
        when(proposalService.getOwned(7L, 31L)).thenReturn(proposal);
        when(runService.findOwnedByIdempotencyKey(7L, "review-same:review:31"))
                .thenReturn(run);

        assertThat(service.review(7L, 31L, "review-same", modelClient)).isSameAs(proposal);

        verify(runService, never()).startRun(any(), any(), any(), any(), any());
        verify(reviewService, never()).review(any(), any(), any(), any());
    }

    @Test
    void shouldBindAReviewRunIdempotencyKeyToTheProposal() {
        RepairProposal proposal = proposal(RepairProposalStatus.DRAFT);
        AgentRun run = new AgentRun();
        run.setId(51L);
        run.setBadCaseId(41L);
        run.setStatus(AgentRunStatus.RUNNING.name());
        when(proposalService.getOwned(7L, 31L)).thenReturn(proposal);
        when(runService.startRun(eq(7L), eq(41L), any(), any(),
                eq("review-request:review:31"))).thenReturn(run);
        when(reviewService.review(eq(7L), eq(31L), any(), eq(modelClient)))
                .thenReturn(proposal);

        assertThat(service.review(7L, 31L, "review-request", modelClient))
                .isSameAs(proposal);

        verify(reviewService).review(eq(7L), eq(31L), any(), eq(modelClient));
    }

    private RepairProposal proposal(RepairProposalStatus status) {
        RepairProposal proposal = new RepairProposal();
        proposal.setId(31L);
        proposal.setUserId(7L);
        proposal.setBadCaseId(41L);
        proposal.setStatus(status.name());
        return proposal;
    }
}
