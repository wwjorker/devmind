package com.devmind.module.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devmind.common.api.ResultCode;
import com.devmind.common.exception.BizException;
import com.devmind.module.ai.agent.AgentBudgetLimits;
import com.devmind.module.ai.agent.AgentBudgetRejection;
import com.devmind.module.ai.agent.AgentExperimentArm;
import com.devmind.module.ai.agent.AgentModelResponse;
import com.devmind.module.ai.agent.AgentRole;
import com.devmind.module.ai.agent.AgentRunStatus;
import com.devmind.module.ai.agent.AgentStepReservation;
import com.devmind.module.ai.agent.AgentStepStatus;
import com.devmind.module.ai.agent.AgentStepType;
import com.devmind.module.ai.agent.AgentTokenUsage;
import com.devmind.module.ai.entity.AgentRun;
import com.devmind.module.ai.entity.AgentStep;
import com.devmind.module.ai.mapper.AgentRunMapper;
import com.devmind.module.ai.mapper.AgentStepMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class AgentRunPersistenceService {

    private final AgentRunMapper runMapper;
    private final AgentStepMapper stepMapper;
    private final Clock clock;

    @Autowired
    public AgentRunPersistenceService(AgentRunMapper runMapper, AgentStepMapper stepMapper) {
        this(runMapper, stepMapper, Clock.systemDefaultZone());
    }

    AgentRunPersistenceService(AgentRunMapper runMapper, AgentStepMapper stepMapper, Clock clock) {
        this.runMapper = runMapper;
        this.stepMapper = stepMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AgentRun startRun(Long userId,
                             Long badCaseId,
                             AgentExperimentArm experimentArm,
                             AgentBudgetLimits limits,
                             String idempotencyKey) {
        requirePositive(userId, "userId");
        Objects.requireNonNull(experimentArm, "experimentArm must not be null");
        Objects.requireNonNull(limits, "limits must not be null");
        String safeIdempotencyKey = requireIdempotencyKey(idempotencyKey);

        AgentRun existing = findByIdempotencyKey(userId, safeIdempotencyKey);
        if (existing != null) {
            ensureEquivalent(existing, badCaseId, experimentArm, limits);
            return existing;
        }

        LocalDateTime startedAt = now();
        AgentRun run = new AgentRun();
        run.setUserId(userId);
        run.setBadCaseId(badCaseId);
        run.setExperimentArm(experimentArm.wireValue());
        run.setStatus(AgentRunStatus.RUNNING.name());
        run.setMaxSteps(limits.maxSteps());
        run.setMaxModelCalls(limits.maxModelCalls());
        run.setMaxTotalTokens(limits.maxTotalTokens());
        run.setTimeoutMs(limits.timeout().toMillis());
        run.setUsedSteps(0);
        run.setUsedModelCalls(0);
        run.setUsedPromptTokens(0);
        run.setUsedCompletionTokens(0);
        run.setUsedTotalTokens(0);
        run.setIdempotencyKey(safeIdempotencyKey);
        run.setStartedAt(startedAt);
        run.setDeadlineAt(startedAt.plus(limits.timeout()));
        try {
            runMapper.insert(run);
            return run;
        } catch (DuplicateKeyException ex) {
            AgentRun concurrent = findByIdempotencyKey(userId, safeIdempotencyKey);
            if (concurrent == null) {
                throw ex;
            }
            ensureEquivalent(concurrent, badCaseId, experimentArm, limits);
            return concurrent;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AgentStepReservation reserveModelStep(Long userId,
                                                 Long runId,
                                                 AgentRole role,
                                                 String inputSummary) {
        Objects.requireNonNull(role, "role must not be null");
        AgentRun run = findOwnedForUpdate(userId, runId);
        AgentRunStatus status = AgentRunStatus.valueOf(run.getStatus());
        if (status != AgentRunStatus.RUNNING) {
            return AgentStepReservation.rejected(status, AgentBudgetRejection.RUN_NOT_ACTIVE);
        }

        LocalDateTime now = now();
        if (!now.isBefore(run.getDeadlineAt())) {
            finishRun(run, AgentRunStatus.TIMED_OUT,
                    AgentBudgetRejection.DEADLINE_EXCEEDED.name(),
                    "agent run deadline exceeded", now);
            return AgentStepReservation.rejected(
                    AgentRunStatus.TIMED_OUT, AgentBudgetRejection.DEADLINE_EXCEEDED);
        }

        AgentBudgetRejection rejection = exhaustedBudget(run);
        if (rejection != AgentBudgetRejection.NONE) {
            if (hasRunningStep(userId, runId)) {
                return AgentStepReservation.rejected(AgentRunStatus.RUNNING, rejection);
            }
            finishRun(run, AgentRunStatus.BUDGET_EXHAUSTED,
                    rejection.name(), "agent run budget exhausted", now);
            return AgentStepReservation.rejected(AgentRunStatus.BUDGET_EXHAUSTED, rejection);
        }

        int sequenceNo = run.getUsedSteps() + 1;
        run.setUsedSteps(sequenceNo);
        run.setUsedModelCalls(run.getUsedModelCalls() + 1);
        runMapper.updateById(run);

        AgentStep step = new AgentStep();
        step.setRunId(runId);
        step.setUserId(userId);
        step.setSequenceNo(sequenceNo);
        step.setRoleName(role.name());
        step.setStepType(AgentStepType.MODEL_CALL.name());
        step.setStatus(AgentStepStatus.RUNNING.name());
        step.setInputSummary(AgentAuditSummaries.result(inputSummary));
        step.setPromptTokens(0);
        step.setCompletionTokens(0);
        step.setTotalTokens(0);
        step.setElapsedMs(0L);
        step.setStartedAt(now);
        stepMapper.insert(step);
        return AgentStepReservation.allowed(step.getId(), sequenceNo);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AgentRunStatus completeModelStep(Long userId,
                                            Long runId,
                                            Long stepId,
                                            AgentModelResponse response,
                                            long elapsedMs,
                                            String outputSummary) {
        Objects.requireNonNull(response, "response must not be null");
        AgentRun run = findOwnedForUpdate(userId, runId);
        AgentStep step = findRunningStep(userId, runId, stepId);
        LocalDateTime completedAt = now();
        AgentTokenUsage usage = response.usage();
        int promptTokens = tokenValue(usage == null ? null : usage.promptTokens());
        int completionTokens = tokenValue(usage == null ? null : usage.completionTokens());
        int totalTokens = Math.max(
                tokenValue(usage == null ? null : usage.totalTokens()),
                promptTokens + completionTokens
        );

        step.setStatus(AgentStepStatus.SUCCEEDED.name());
        step.setOutputSummary(AgentAuditSummaries.result(outputSummary));
        step.setPromptTokens(promptTokens);
        step.setCompletionTokens(completionTokens);
        step.setTotalTokens(totalTokens);
        step.setElapsedMs(Math.max(elapsedMs, 0));
        step.setCompletedAt(completedAt);
        stepMapper.updateById(step);

        run.setUsedPromptTokens(run.getUsedPromptTokens() + promptTokens);
        run.setUsedCompletionTokens(run.getUsedCompletionTokens() + completionTokens);
        run.setUsedTotalTokens(run.getUsedTotalTokens() + totalTokens);
        AgentRunStatus runStatus = AgentRunStatus.valueOf(run.getStatus());
        if (runStatus == AgentRunStatus.RUNNING && !completedAt.isBefore(run.getDeadlineAt())) {
            finishRun(run, AgentRunStatus.TIMED_OUT,
                    AgentBudgetRejection.DEADLINE_EXCEEDED.name(),
                    "agent model call completed after the run deadline", completedAt);
            return AgentRunStatus.TIMED_OUT;
        }
        runMapper.updateById(run);
        return runStatus;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AgentRunStatus failModelStep(Long userId,
                                        Long runId,
                                        Long stepId,
                                        RuntimeException error,
                                        long elapsedMs) {
        AgentRun run = findOwnedForUpdate(userId, runId);
        AgentStep step = findRunningStep(userId, runId, stepId);
        LocalDateTime completedAt = now();
        String errorCode = AgentAuditSummaries.errorCode(error);
        String errorMessage = AgentAuditSummaries.errorMessage(error);

        step.setStatus(AgentStepStatus.FAILED.name());
        step.setElapsedMs(Math.max(elapsedMs, 0));
        step.setErrorCode(errorCode);
        step.setErrorMessage(errorMessage);
        step.setCompletedAt(completedAt);
        stepMapper.updateById(step);

        AgentRunStatus current = AgentRunStatus.valueOf(run.getStatus());
        if (current != AgentRunStatus.RUNNING) {
            return current;
        }
        AgentRunStatus failureStatus = completedAt.isBefore(run.getDeadlineAt())
                ? AgentRunStatus.FAILED
                : AgentRunStatus.TIMED_OUT;
        finishRun(run, failureStatus, errorCode, errorMessage, completedAt);
        return failureStatus;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AgentRunStatus markSucceeded(Long userId, Long runId, String resultSummary) {
        AgentRun run = findOwnedForUpdate(userId, runId);
        AgentRunStatus current = AgentRunStatus.valueOf(run.getStatus());
        if (current != AgentRunStatus.RUNNING) {
            return current;
        }
        LocalDateTime completedAt = now();
        if (!completedAt.isBefore(run.getDeadlineAt())) {
            finishRun(run, AgentRunStatus.TIMED_OUT,
                    AgentBudgetRejection.DEADLINE_EXCEEDED.name(),
                    "agent run deadline exceeded before completion", completedAt);
            return AgentRunStatus.TIMED_OUT;
        }
        run.setResultSummary(AgentAuditSummaries.result(resultSummary));
        finishRun(run, AgentRunStatus.SUCCEEDED, null, null, completedAt);
        return AgentRunStatus.SUCCEEDED;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AgentRunStatus cancelRun(Long userId, Long runId) {
        AgentRun run = findOwnedForUpdate(userId, runId);
        AgentRunStatus current = AgentRunStatus.valueOf(run.getStatus());
        if (current != AgentRunStatus.RUNNING) {
            return current;
        }
        finishRun(run, AgentRunStatus.CANCELLED, "CANCELLED", "agent run cancelled", now());
        return AgentRunStatus.CANCELLED;
    }

    public AgentRun getOwnedRun(Long userId, Long runId) {
        AgentRun run = runMapper.selectOne(new LambdaQueryWrapper<AgentRun>()
                .eq(AgentRun::getId, runId)
                .eq(AgentRun::getUserId, userId));
        if (run == null) {
            throw new BizException(ResultCode.NOT_FOUND, "agent run not found");
        }
        return run;
    }

    public List<AgentStep> replaySteps(Long userId, Long runId) {
        getOwnedRun(userId, runId);
        return stepMapper.selectList(new LambdaQueryWrapper<AgentStep>()
                .eq(AgentStep::getUserId, userId)
                .eq(AgentStep::getRunId, runId)
                .orderByAsc(AgentStep::getSequenceNo));
    }

    private AgentBudgetRejection exhaustedBudget(AgentRun run) {
        if (run.getUsedSteps() >= run.getMaxSteps()) {
            return AgentBudgetRejection.MAX_STEPS;
        }
        if (run.getUsedModelCalls() >= run.getMaxModelCalls()) {
            return AgentBudgetRejection.MAX_MODEL_CALLS;
        }
        if (run.getUsedTotalTokens() >= run.getMaxTotalTokens()) {
            return AgentBudgetRejection.MAX_TOTAL_TOKENS;
        }
        return AgentBudgetRejection.NONE;
    }

    private AgentRun findOwnedForUpdate(Long userId, Long runId) {
        requirePositive(userId, "userId");
        requirePositive(runId, "runId");
        AgentRun run = runMapper.selectOwnedForUpdate(userId, runId);
        if (run == null) {
            throw new BizException(ResultCode.NOT_FOUND, "agent run not found");
        }
        return run;
    }

    private AgentStep findRunningStep(Long userId, Long runId, Long stepId) {
        AgentStep step = stepMapper.selectOne(new LambdaQueryWrapper<AgentStep>()
                .eq(AgentStep::getId, stepId)
                .eq(AgentStep::getRunId, runId)
                .eq(AgentStep::getUserId, userId));
        if (step == null) {
            throw new BizException(ResultCode.NOT_FOUND, "agent step not found");
        }
        if (!AgentStepStatus.RUNNING.name().equals(step.getStatus())) {
            throw new BizException(ResultCode.CONFLICT, "agent step is already complete");
        }
        return step;
    }

    private boolean hasRunningStep(Long userId, Long runId) {
        return stepMapper.selectCount(new LambdaQueryWrapper<AgentStep>()
                .eq(AgentStep::getUserId, userId)
                .eq(AgentStep::getRunId, runId)
                .eq(AgentStep::getStatus, AgentStepStatus.RUNNING.name())) > 0;
    }

    private AgentRun findByIdempotencyKey(Long userId, String idempotencyKey) {
        return runMapper.selectOne(new LambdaQueryWrapper<AgentRun>()
                .eq(AgentRun::getUserId, userId)
                .eq(AgentRun::getIdempotencyKey, idempotencyKey));
    }

    private void ensureEquivalent(AgentRun existing,
                                  Long badCaseId,
                                  AgentExperimentArm experimentArm,
                                  AgentBudgetLimits limits) {
        boolean equivalent = Objects.equals(existing.getBadCaseId(), badCaseId)
                && experimentArm.wireValue().equals(existing.getExperimentArm())
                && existing.getMaxSteps() == limits.maxSteps()
                && existing.getMaxModelCalls() == limits.maxModelCalls()
                && existing.getMaxTotalTokens() == limits.maxTotalTokens()
                && existing.getTimeoutMs() == limits.timeout().toMillis();
        if (!equivalent) {
            throw new BizException(ResultCode.CONFLICT,
                    "idempotency key already belongs to a different agent run request");
        }
    }

    private void finishRun(AgentRun run,
                           AgentRunStatus status,
                           String errorCode,
                           String errorMessage,
                           LocalDateTime completedAt) {
        run.setStatus(status.name());
        run.setErrorCode(errorCode);
        run.setErrorMessage(errorMessage);
        run.setCompletedAt(completedAt);
        runMapper.updateById(run);
    }

    private int tokenValue(Integer value) {
        return value == null ? 0 : Math.max(value, 0);
    }

    private String requireIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new BizException(ResultCode.BAD_REQUEST, "agent run idempotency key is required");
        }
        String value = idempotencyKey.trim();
        if (value.length() > 128) {
            throw new BizException(ResultCode.BAD_REQUEST, "agent run idempotency key is too long");
        }
        return value;
    }

    private void requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, field + " must be positive");
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
