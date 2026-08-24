package com.devmind.module.ai.service;

import com.devmind.module.ai.agent.AgentModelClient;
import com.devmind.module.ai.agent.AgentRunStatus;
import com.devmind.module.ai.agent.BadCaseStatus;
import com.devmind.module.ai.agent.RepairProposalType;
import com.devmind.module.ai.agent.TriageDiagnosis;
import com.devmind.module.ai.agent.TriageEvidence;
import com.devmind.module.ai.agent.TriageProposalCandidate;
import com.devmind.module.ai.agent.TriageRootCause;
import com.devmind.module.ai.entity.AgentRun;
import com.devmind.module.ai.entity.AiBadCase;
import com.devmind.module.ai.entity.RepairProposal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentOrchestratorTest {

    @Mock private BadCaseIntakeService intakeService;
    @Mock private AgentRunPersistenceService runService;
    @Mock private EvidenceTriageAgent triageAgent;
    @Mock private ProposalValidator proposalValidator;
    @Mock private RepairProposalService proposalService;
    @Mock private TriageWorkflowPersistenceService workflowPersistenceService;
    @Mock private AgentModelClient modelClient;

    private AgentOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new AgentOrchestrator(
                intakeService,
                runService,
                triageAgent,
                proposalValidator,
                proposalService,
                workflowPersistenceService,
                new ObjectMapper());
    }

    @Test
    void shouldValidateAndPersistATriageMetadataProposalBeforeCompletingTheRun() {
        AiBadCase badCase = badCase(9L, BadCaseStatus.NEW);
        AgentRun run = runningRun(20L, badCase.getId());
        TriageProposalCandidate candidate = new TriageProposalCandidate(
                RepairProposalType.METADATA_PATCH,
                31L,
                2,
                "{\"tags\":\"spring,propagation\"}",
                "[]",
                "[]",
                "{\"summary\":\"improve recall\"}",
                "{\"targetQuestion\":\"How does propagation work?\"}");
        TriageDiagnosis diagnosis = new TriageDiagnosis(
                TriageRootCause.KNOWLEDGE_EXISTS_NOT_RETRIEVED,
                "The current document was not retrieved.",
                List.of(new TriageEvidence(
                        "search-1", null, 301L, "Current evidence was found.")),
                TriageRootCause.KNOWLEDGE_EXISTS_NOT_RETRIEVED.requiredRoute(),
                0.9,
                candidate);
        AiBadCase triaged = badCase(9L, BadCaseStatus.TRIAGED);
        RepairProposal proposal = new RepairProposal();
        proposal.setId(88L);
        proposal.setStatus("DRAFT");
        when(intakeService.getOwned(7L, 9L)).thenReturn(badCase);
        when(runService.startRun(eq(7L), eq(9L), any(), any(), eq("wf-1:triage")))
                .thenReturn(run);
        when(triageAgent.triageForWorkflow(any(), eq(modelClient), any()))
                .thenReturn(diagnosis);
        AgentRun completed = runningRun(20L, 9L);
        completed.setStatus(AgentRunStatus.SUCCEEDED.name());
        when(workflowPersistenceService.persist(
                7L, 9L, 20L, "wf-1:proposal", diagnosis))
                .thenReturn(new TriageWorkflowResult(completed, triaged, proposal));

        TriageWorkflowResult result = orchestrator.triageAndRoute(
                7L, 9L, "wf-1", modelClient);

        assertThat(result.proposal().getId()).isEqualTo(88L);
        verify(proposalValidator).validate(eq(7L), eq(9L), any());
        verify(workflowPersistenceService).persist(
                7L, 9L, 20L, "wf-1:proposal", diagnosis);
    }

    @Test
    void shouldRouteOutOfScopeWithoutCreatingAProposal() {
        AiBadCase badCase = badCase(10L, BadCaseStatus.NEW);
        AgentRun run = runningRun(21L, badCase.getId());
        TriageDiagnosis diagnosis = new TriageDiagnosis(
                TriageRootCause.OUT_OF_KNOWLEDGE_SCOPE,
                "The request requires live external data.",
                List.of(new TriageEvidence(
                        "ask-1", 100L, null, "The target ask was inspected.")),
                TriageRootCause.OUT_OF_KNOWLEDGE_SCOPE.requiredRoute(),
                0.95);
        AiBadCase triaged = badCase(10L, BadCaseStatus.TRIAGED);
        when(intakeService.getOwned(7L, 10L)).thenReturn(badCase);
        when(runService.startRun(eq(7L), eq(10L), any(), any(), eq("wf-2:triage")))
                .thenReturn(run);
        when(triageAgent.triageForWorkflow(any(), eq(modelClient), any()))
                .thenReturn(diagnosis);
        AgentRun completed = runningRun(21L, 10L);
        completed.setStatus(AgentRunStatus.SUCCEEDED.name());
        when(workflowPersistenceService.persist(
                7L, 10L, 21L, "wf-2:proposal", diagnosis))
                .thenReturn(new TriageWorkflowResult(completed, triaged, null));

        TriageWorkflowResult result = orchestrator.triageAndRoute(
                7L, 10L, "wf-2", modelClient);

        assertThat(result.proposal()).isNull();
        verify(proposalValidator, never()).validate(any(), any(), any());
        verify(proposalService, never()).create(any(), any(), any(), any());
        verify(workflowPersistenceService).persist(
                7L, 10L, 21L, "wf-2:proposal", diagnosis);
    }

    @Test
    void shouldRejectATerminalCaseWithoutCreatingAnOrphanRun() {
        AiBadCase badCase = badCase(11L, BadCaseStatus.NO_ACTION);
        when(intakeService.getOwned(7L, 11L)).thenReturn(badCase);

        assertThatThrownBy(() -> orchestrator.triageAndRoute(
                7L, 11L, "wf-terminal", modelClient))
                .isInstanceOf(com.devmind.common.exception.BizException.class)
                .hasMessageContaining("not awaiting triage");

        verify(runService, never()).startRun(any(), any(), any(), any(), any());
        verify(triageAgent, never()).triageForWorkflow(any(), any(), any());
    }

    @Test
    void shouldCloseTheRunWhenSnapshotPreparationFails() {
        AiBadCase badCase = badCase(12L, BadCaseStatus.NEW);
        badCase.setAskSnapshotJson("not-json");
        AgentRun run = runningRun(22L, 12L);
        when(intakeService.getOwned(7L, 12L)).thenReturn(badCase);
        when(runService.startRun(eq(7L), eq(12L), any(), any(), eq("wf-invalid:triage")))
                .thenReturn(run);

        assertThatThrownBy(() -> orchestrator.triageAndRoute(
                7L, 12L, "wf-invalid", modelClient))
                .isInstanceOf(com.devmind.common.exception.BizException.class)
                .hasMessageContaining("snapshot is invalid");

        verify(runService).failRunIfActive(
                7L, 22L, "TRIAGE_WORKFLOW_FAILED", "triage workflow failed");
        verify(triageAgent, never()).triageForWorkflow(any(), any(), any());
    }

    private AiBadCase badCase(Long id, BadCaseStatus status) {
        AiBadCase badCase = new AiBadCase();
        badCase.setId(id);
        badCase.setUserId(7L);
        badCase.setAskLogId(100L);
        badCase.setAskSnapshotJson("{\"askLogId\":100,\"question\":\"Question\","
                + "\"answer\":\"Answer\",\"reason\":\"Unhelpful\"}");
        badCase.setStatus(status.name());
        return badCase;
    }

    private AgentRun runningRun(Long id, Long badCaseId) {
        AgentRun run = new AgentRun();
        run.setId(id);
        run.setUserId(7L);
        run.setBadCaseId(badCaseId);
        run.setStatus(AgentRunStatus.RUNNING.name());
        return run;
    }
}
