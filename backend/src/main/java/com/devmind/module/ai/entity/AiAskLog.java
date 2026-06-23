package com.devmind.module.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("ai_ask_log")
public class AiAskLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String question;
    private String retrievalKeyword;
    private String promptPreview;
    private String answer;
    private String modelProvider;
    private Boolean mock;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Integer retrievedChunkCount;
    private String retrievedChunkIds;
    private Long elapsedMs;
    private Integer status;
    private LocalDateTime createdAt;

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

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getRetrievalKeyword() {
        return retrievalKeyword;
    }

    public void setRetrievalKeyword(String retrievalKeyword) {
        this.retrievalKeyword = retrievalKeyword;
    }

    public String getPromptPreview() {
        return promptPreview;
    }

    public void setPromptPreview(String promptPreview) {
        this.promptPreview = promptPreview;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getModelProvider() {
        return modelProvider;
    }

    public void setModelProvider(String modelProvider) {
        this.modelProvider = modelProvider;
    }

    public Boolean getMock() {
        return mock;
    }

    public void setMock(Boolean mock) {
        this.mock = mock;
    }

    public Integer getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(Integer promptTokens) {
        this.promptTokens = promptTokens;
    }

    public Integer getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(Integer completionTokens) {
        this.completionTokens = completionTokens;
    }

    public Integer getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(Integer totalTokens) {
        this.totalTokens = totalTokens;
    }

    public Integer getRetrievedChunkCount() {
        return retrievedChunkCount;
    }

    public void setRetrievedChunkCount(Integer retrievedChunkCount) {
        this.retrievedChunkCount = retrievedChunkCount;
    }

    public String getRetrievedChunkIds() {
        return retrievedChunkIds;
    }

    public void setRetrievedChunkIds(String retrievedChunkIds) {
        this.retrievedChunkIds = retrievedChunkIds;
    }

    public Long getElapsedMs() {
        return elapsedMs;
    }

    public void setElapsedMs(Long elapsedMs) {
        this.elapsedMs = elapsedMs;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
