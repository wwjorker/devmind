package com.devmind.module.ai.vo;

import java.time.LocalDateTime;
import java.util.List;

public class RagEvaluationCaseResponse {

    private String caseId;
    private String category;
    private String question;
    private List<String> expectedKeywords;
    private String expectedAnswer;
    private String expectedEvidence;
    private String riskType;
    private Boolean covered;
    private Long lastAskLogId;
    private Integer lastStatus;
    private Integer lastRetrievedChunkCount;
    private String lastRetrievedChunkIds;
    private LocalDateTime lastAskedAt;

    public RagEvaluationCaseResponse() {
    }

    public RagEvaluationCaseResponse(String caseId,
                                     String category,
                                     String question,
                                     List<String> expectedKeywords,
                                     String expectedAnswer,
                                     String expectedEvidence,
                                     String riskType,
                                     Boolean covered,
                                     Long lastAskLogId,
                                     Integer lastStatus,
                                     Integer lastRetrievedChunkCount,
                                     String lastRetrievedChunkIds,
                                     LocalDateTime lastAskedAt) {
        this.caseId = caseId;
        this.category = category;
        this.question = question;
        this.expectedKeywords = expectedKeywords;
        this.expectedAnswer = expectedAnswer;
        this.expectedEvidence = expectedEvidence;
        this.riskType = riskType;
        this.covered = covered;
        this.lastAskLogId = lastAskLogId;
        this.lastStatus = lastStatus;
        this.lastRetrievedChunkCount = lastRetrievedChunkCount;
        this.lastRetrievedChunkIds = lastRetrievedChunkIds;
        this.lastAskedAt = lastAskedAt;
    }

    public String getCaseId() {
        return caseId;
    }

    public void setCaseId(String caseId) {
        this.caseId = caseId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public List<String> getExpectedKeywords() {
        return expectedKeywords;
    }

    public void setExpectedKeywords(List<String> expectedKeywords) {
        this.expectedKeywords = expectedKeywords;
    }

    public String getExpectedAnswer() {
        return expectedAnswer;
    }

    public void setExpectedAnswer(String expectedAnswer) {
        this.expectedAnswer = expectedAnswer;
    }

    public String getExpectedEvidence() {
        return expectedEvidence;
    }

    public void setExpectedEvidence(String expectedEvidence) {
        this.expectedEvidence = expectedEvidence;
    }

    public String getRiskType() {
        return riskType;
    }

    public void setRiskType(String riskType) {
        this.riskType = riskType;
    }

    public Boolean getCovered() {
        return covered;
    }

    public void setCovered(Boolean covered) {
        this.covered = covered;
    }

    public Long getLastAskLogId() {
        return lastAskLogId;
    }

    public void setLastAskLogId(Long lastAskLogId) {
        this.lastAskLogId = lastAskLogId;
    }

    public Integer getLastStatus() {
        return lastStatus;
    }

    public void setLastStatus(Integer lastStatus) {
        this.lastStatus = lastStatus;
    }

    public Integer getLastRetrievedChunkCount() {
        return lastRetrievedChunkCount;
    }

    public void setLastRetrievedChunkCount(Integer lastRetrievedChunkCount) {
        this.lastRetrievedChunkCount = lastRetrievedChunkCount;
    }

    public String getLastRetrievedChunkIds() {
        return lastRetrievedChunkIds;
    }

    public void setLastRetrievedChunkIds(String lastRetrievedChunkIds) {
        this.lastRetrievedChunkIds = lastRetrievedChunkIds;
    }

    public LocalDateTime getLastAskedAt() {
        return lastAskedAt;
    }

    public void setLastAskedAt(LocalDateTime lastAskedAt) {
        this.lastAskedAt = lastAskedAt;
    }
}
