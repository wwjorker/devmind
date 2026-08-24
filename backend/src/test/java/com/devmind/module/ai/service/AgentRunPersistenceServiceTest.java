package com.devmind.module.ai.service;

import com.devmind.module.ai.agent.AgentBudgetLimits;
import com.devmind.module.ai.agent.AgentBudgetRejection;
import com.devmind.module.ai.agent.AgentExperimentArm;
import com.devmind.module.ai.agent.AgentRole;
import com.devmind.module.ai.agent.AgentRunStatus;
import com.devmind.module.ai.agent.AgentStepReservation;
import com.devmind.module.ai.entity.AgentRun;
import com.devmind.module.ai.entity.AgentStep;
import com.devmind.module.ai.mapper.AgentRunMapper;
import com.devmind.module.ai.mapper.AgentStepMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentRunPersistenceServiceTest {

    @Test
    void shouldPersistTimeoutWithoutCreatingAnotherStep() {
        AgentRunMapper runMapper = mock(AgentRunMapper.class);
        AgentStepMapper stepMapper = mock(AgentStepMapper.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-17T00:00:00Z"), ZoneOffset.UTC);
        AgentRunPersistenceService service = new AgentRunPersistenceService(
                runMapper, stepMapper, clock);
        AgentRun run = runningRun();
        run.setDeadlineAt(LocalDateTime.ofInstant(
                Instant.parse("2026-08-16T23:59:59Z"), ZoneOffset.UTC));
        when(runMapper.selectOwnedForUpdate(7L, 42L)).thenReturn(run);

        AgentStepReservation reservation = service.reserveModelStep(
                7L, 42L, AgentRole.EVIDENCE_TRIAGE, "safe summary");

        assertThat(reservation.permitted()).isFalse();
        assertThat(reservation.runStatus()).isEqualTo(AgentRunStatus.TIMED_OUT);
        assertThat(reservation.rejection()).isEqualTo(AgentBudgetRejection.DEADLINE_EXCEEDED);
        assertThat(run.getStatus()).isEqualTo(AgentRunStatus.TIMED_OUT.name());
        assertThat(run.getCompletedAt()).isEqualTo(LocalDateTime.of(
                2026, 8, 17, 0, 0));
        verify(runMapper).updateById(run);
        verify(stepMapper, never()).insert(any(AgentStep.class));
    }

    @Test
    void shouldRecoverConcurrentIdempotentInsertWithALockingCurrentRead() {
        AgentRunMapper runMapper = mock(AgentRunMapper.class);
        AgentStepMapper stepMapper = mock(AgentStepMapper.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-17T00:00:00Z"), ZoneOffset.UTC);
        AgentRunPersistenceService service = new AgentRunPersistenceService(
                runMapper, stepMapper, clock);
        AgentBudgetLimits limits = new AgentBudgetLimits(
                18, 6, 12, 24_000, Duration.ofSeconds(120));
        AgentRun concurrent = runningRun();
        concurrent.setId(99L);
        concurrent.setExperimentArm(AgentExperimentArm.SINGLE.wireValue());
        concurrent.setMaxSteps(limits.maxSteps());
        concurrent.setMaxModelCalls(limits.maxModelCalls());
        concurrent.setMaxToolCalls(limits.maxToolCalls());
        concurrent.setMaxTotalTokens(limits.maxTotalTokens());
        concurrent.setTimeoutMs(limits.timeout().toMillis());
        concurrent.setIdempotencyKey("same-key");
        when(runMapper.insert(any(AgentRun.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));
        when(runMapper.selectByIdempotencyKeyForUpdate(7L, "same-key"))
                .thenReturn(concurrent);

        AgentRun recovered = service.startRun(
                7L, null, AgentExperimentArm.SINGLE, limits, "same-key");

        assertThat(recovered).isSameAs(concurrent);
        verify(runMapper).selectByIdempotencyKeyForUpdate(7L, "same-key");
    }

    private AgentRun runningRun() {
        AgentRun run = new AgentRun();
        run.setId(42L);
        run.setUserId(7L);
        run.setStatus(AgentRunStatus.RUNNING.name());
        run.setMaxSteps(6);
        run.setMaxModelCalls(6);
        run.setMaxToolCalls(6);
        run.setMaxTotalTokens(20_000);
        run.setUsedSteps(0);
        run.setUsedModelCalls(0);
        run.setUsedToolCalls(0);
        run.setUsedPromptTokens(0);
        run.setUsedCompletionTokens(0);
        run.setUsedTotalTokens(0);
        return run;
    }
}
