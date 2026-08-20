package com.devmind.module.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("agent_run")
public class AgentRun {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long badCaseId;
    private String experimentArm;
    private String status;
    private Integer maxSteps;
    private Integer maxModelCalls;
    private Integer maxToolCalls;
    private Integer maxTotalTokens;
    private Long timeoutMs;
    private Integer usedSteps;
    private Integer usedModelCalls;
    private Integer usedToolCalls;
    private Integer usedPromptTokens;
    private Integer usedCompletionTokens;
    private Integer usedTotalTokens;
    private String resultSummary;
    private String errorCode;
    private String errorMessage;
    private String idempotencyKey;
    private LocalDateTime startedAt;
    private LocalDateTime deadlineAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getBadCaseId() {
        return badCaseId;
    }

    public void setBadCaseId(Long badCaseId) {
        this.badCaseId = badCaseId;
    }

    public String getExperimentArm() {
        return experimentArm;
    }

    public void setExperimentArm(String experimentArm) {
        this.experimentArm = experimentArm;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getMaxSteps() {
        return maxSteps;
    }

    public void setMaxSteps(Integer maxSteps) {
        this.maxSteps = maxSteps;
    }

    public Integer getMaxModelCalls() {
        return maxModelCalls;
    }

    public void setMaxModelCalls(Integer maxModelCalls) {
        this.maxModelCalls = maxModelCalls;
    }

    public Integer getMaxToolCalls() {
        return maxToolCalls;
    }

    public void setMaxToolCalls(Integer maxToolCalls) {
        this.maxToolCalls = maxToolCalls;
    }

    public Integer getMaxTotalTokens() {
        return maxTotalTokens;
    }

    public void setMaxTotalTokens(Integer maxTotalTokens) {
        this.maxTotalTokens = maxTotalTokens;
    }

    public Long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(Long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public Integer getUsedSteps() {
        return usedSteps;
    }

    public void setUsedSteps(Integer usedSteps) {
        this.usedSteps = usedSteps;
    }

    public Integer getUsedModelCalls() {
        return usedModelCalls;
    }

    public void setUsedModelCalls(Integer usedModelCalls) {
        this.usedModelCalls = usedModelCalls;
    }

    public Integer getUsedToolCalls() {
        return usedToolCalls;
    }

    public void setUsedToolCalls(Integer usedToolCalls) {
        this.usedToolCalls = usedToolCalls;
    }

    public Integer getUsedPromptTokens() {
        return usedPromptTokens;
    }

    public void setUsedPromptTokens(Integer usedPromptTokens) {
        this.usedPromptTokens = usedPromptTokens;
    }

    public Integer getUsedCompletionTokens() {
        return usedCompletionTokens;
    }

    public void setUsedCompletionTokens(Integer usedCompletionTokens) {
        this.usedCompletionTokens = usedCompletionTokens;
    }

    public Integer getUsedTotalTokens() {
        return usedTotalTokens;
    }

    public void setUsedTotalTokens(Integer usedTotalTokens) {
        this.usedTotalTokens = usedTotalTokens;
    }

    public String getResultSummary() {
        return resultSummary;
    }

    public void setResultSummary(String resultSummary) {
        this.resultSummary = resultSummary;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getDeadlineAt() {
        return deadlineAt;
    }

    public void setDeadlineAt(LocalDateTime deadlineAt) {
        this.deadlineAt = deadlineAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
