package com.devmind.module.ai.vo;

import java.time.LocalDateTime;

public class AskLogResponse {

    private Long id;
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

    public AskLogResponse() {
    }

    public AskLogResponse(Long id,
                          String question,
                          String retrievalKeyword,
                          String promptPreview,
                          String answer,
                          String modelProvider,
                          Boolean mock,
                          Integer promptTokens,
                          Integer completionTokens,
                          Integer totalTokens,
                          Integer retrievedChunkCount,
                          String retrievedChunkIds,
                          Long elapsedMs,
                          Integer status,
                          LocalDateTime createdAt) {
        this.id = id;
        this.question = question;
        this.retrievalKeyword = retrievalKeyword;
        this.promptPreview = promptPreview;
        this.answer = answer;
        this.modelProvider = modelProvider;
        this.mock = mock;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
        this.retrievedChunkCount = retrievedChunkCount;
        this.retrievedChunkIds = retrievedChunkIds;
        this.elapsedMs = elapsedMs;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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
