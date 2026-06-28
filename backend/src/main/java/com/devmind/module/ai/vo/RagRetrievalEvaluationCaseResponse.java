package com.devmind.module.ai.vo;

import java.util.List;

public class RagRetrievalEvaluationCaseResponse {

    private String caseId;
    private String category;
    private String question;
    private List<String> expectedKeywords;
    private List<String> queryKeywords;
    private List<String> matchedExpectedKeywords;
    private List<String> missingExpectedKeywords;
    private String expectedEvidence;
    private String riskType;
    private Boolean passed;
    private Boolean expectedNoContext;
    private Integer retrievedChunkCount;
    private List<Long> topChunkIds;
    private List<String> topDocumentTitles;
    private String note;

    public RagRetrievalEvaluationCaseResponse() {
    }

    public RagRetrievalEvaluationCaseResponse(String caseId,
                                              String category,
                                              String question,
                                              List<String> expectedKeywords,
                                              List<String> queryKeywords,
                                              List<String> matchedExpectedKeywords,
                                              List<String> missingExpectedKeywords,
                                              String expectedEvidence,
                                              String riskType,
                                              Boolean passed,
                                              Boolean expectedNoContext,
                                              Integer retrievedChunkCount,
                                              List<Long> topChunkIds,
                                              List<String> topDocumentTitles,
                                              String note) {
        this.caseId = caseId;
        this.category = category;
        this.question = question;
        this.expectedKeywords = expectedKeywords;
        this.queryKeywords = queryKeywords;
        this.matchedExpectedKeywords = matchedExpectedKeywords;
        this.missingExpectedKeywords = missingExpectedKeywords;
        this.expectedEvidence = expectedEvidence;
        this.riskType = riskType;
        this.passed = passed;
        this.expectedNoContext = expectedNoContext;
        this.retrievedChunkCount = retrievedChunkCount;
        this.topChunkIds = topChunkIds;
        this.topDocumentTitles = topDocumentTitles;
        this.note = note;
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

    public List<String> getQueryKeywords() {
        return queryKeywords;
    }

    public void setQueryKeywords(List<String> queryKeywords) {
        this.queryKeywords = queryKeywords;
    }

    public List<String> getMatchedExpectedKeywords() {
        return matchedExpectedKeywords;
    }

    public void setMatchedExpectedKeywords(List<String> matchedExpectedKeywords) {
        this.matchedExpectedKeywords = matchedExpectedKeywords;
    }

    public List<String> getMissingExpectedKeywords() {
        return missingExpectedKeywords;
    }

    public void setMissingExpectedKeywords(List<String> missingExpectedKeywords) {
        this.missingExpectedKeywords = missingExpectedKeywords;
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

    public Boolean getPassed() {
        return passed;
    }

    public void setPassed(Boolean passed) {
        this.passed = passed;
    }

    public Boolean getExpectedNoContext() {
        return expectedNoContext;
    }

    public void setExpectedNoContext(Boolean expectedNoContext) {
        this.expectedNoContext = expectedNoContext;
    }

    public Integer getRetrievedChunkCount() {
        return retrievedChunkCount;
    }

    public void setRetrievedChunkCount(Integer retrievedChunkCount) {
        this.retrievedChunkCount = retrievedChunkCount;
    }

    public List<Long> getTopChunkIds() {
        return topChunkIds;
    }

    public void setTopChunkIds(List<Long> topChunkIds) {
        this.topChunkIds = topChunkIds;
    }

    public List<String> getTopDocumentTitles() {
        return topDocumentTitles;
    }

    public void setTopDocumentTitles(List<String> topDocumentTitles) {
        this.topDocumentTitles = topDocumentTitles;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
