package com.devmind.module.ai.vo;

import java.util.List;

public class RagRetrievalStrategyEvaluationResponse {

    private String strategyKey;
    private String embeddingProvider;
    private String retrievalStrategy;
    private String retrievalStrategyDescription;
    private String status;
    private String unavailableReason;
    private Integer passedCaseCount;
    private Double passRate;
    private Integer positiveCaseCount;
    private Double hitAtK;
    private Double mrr;
    private Double hitAtKDelta;
    private Double mrrDelta;
    private List<RagRetrievalEvaluationCaseResponse> cases;

    public RagRetrievalStrategyEvaluationResponse() {
    }

    public RagRetrievalStrategyEvaluationResponse(String strategyKey,
                                                  String embeddingProvider,
                                                  String retrievalStrategy,
                                                  String retrievalStrategyDescription,
                                                  String status,
                                                  String unavailableReason,
                                                  Integer passedCaseCount,
                                                  Double passRate,
                                                  Integer positiveCaseCount,
                                                  Double hitAtK,
                                                  Double mrr,
                                                  Double hitAtKDelta,
                                                  Double mrrDelta,
                                                  List<RagRetrievalEvaluationCaseResponse> cases) {
        this.strategyKey = strategyKey;
        this.embeddingProvider = embeddingProvider;
        this.retrievalStrategy = retrievalStrategy;
        this.retrievalStrategyDescription = retrievalStrategyDescription;
        this.status = status;
        this.unavailableReason = unavailableReason;
        this.passedCaseCount = passedCaseCount;
        this.passRate = passRate;
        this.positiveCaseCount = positiveCaseCount;
        this.hitAtK = hitAtK;
        this.mrr = mrr;
        this.hitAtKDelta = hitAtKDelta;
        this.mrrDelta = mrrDelta;
        this.cases = cases;
    }

    public String getStrategyKey() {
        return strategyKey;
    }

    public void setStrategyKey(String strategyKey) {
        this.strategyKey = strategyKey;
    }

    public String getEmbeddingProvider() {
        return embeddingProvider;
    }

    public void setEmbeddingProvider(String embeddingProvider) {
        this.embeddingProvider = embeddingProvider;
    }

    public String getRetrievalStrategy() {
        return retrievalStrategy;
    }

    public void setRetrievalStrategy(String retrievalStrategy) {
        this.retrievalStrategy = retrievalStrategy;
    }

    public String getRetrievalStrategyDescription() {
        return retrievalStrategyDescription;
    }

    public void setRetrievalStrategyDescription(String retrievalStrategyDescription) {
        this.retrievalStrategyDescription = retrievalStrategyDescription;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getUnavailableReason() {
        return unavailableReason;
    }

    public void setUnavailableReason(String unavailableReason) {
        this.unavailableReason = unavailableReason;
    }

    public Integer getPassedCaseCount() {
        return passedCaseCount;
    }

    public void setPassedCaseCount(Integer passedCaseCount) {
        this.passedCaseCount = passedCaseCount;
    }

    public Double getPassRate() {
        return passRate;
    }

    public void setPassRate(Double passRate) {
        this.passRate = passRate;
    }

    public Integer getPositiveCaseCount() {
        return positiveCaseCount;
    }

    public void setPositiveCaseCount(Integer positiveCaseCount) {
        this.positiveCaseCount = positiveCaseCount;
    }

    public Double getHitAtK() {
        return hitAtK;
    }

    public void setHitAtK(Double hitAtK) {
        this.hitAtK = hitAtK;
    }

    public Double getMrr() {
        return mrr;
    }

    public void setMrr(Double mrr) {
        this.mrr = mrr;
    }

    public Double getHitAtKDelta() {
        return hitAtKDelta;
    }

    public void setHitAtKDelta(Double hitAtKDelta) {
        this.hitAtKDelta = hitAtKDelta;
    }

    public Double getMrrDelta() {
        return mrrDelta;
    }

    public void setMrrDelta(Double mrrDelta) {
        this.mrrDelta = mrrDelta;
    }

    public List<RagRetrievalEvaluationCaseResponse> getCases() {
        return cases;
    }

    public void setCases(List<RagRetrievalEvaluationCaseResponse> cases) {
        this.cases = cases;
    }
}
