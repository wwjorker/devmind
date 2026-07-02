package com.devmind.module.ai.vo;

import java.util.List;

public class RagRetrievalEvaluationResponse {

    private int totalCaseCount;
    private int passedCaseCount;
    private double passRate;
    private int positiveCaseCount;
    private int evaluationK;
    private int retrievalLimit;
    private String retrievalStrategy;
    private String retrievalStrategyDescription;
    private String baselineRetrievalStrategy;
    private String baselineRetrievalStrategyDescription;
    private String relevanceMode;
    private double hitAtK;
    private double mrr;
    private int baselinePassedCaseCount;
    private double baselinePassRate;
    private double baselineHitAtK;
    private double baselineMrr;
    private double hitAtKDelta;
    private double mrrDelta;
    private List<RagRetrievalStrategyEvaluationResponse> strategyResults;
    private List<RagRetrievalEvaluationCaseResponse> cases;

    public RagRetrievalEvaluationResponse() {
    }

    public RagRetrievalEvaluationResponse(int totalCaseCount,
                                          int passedCaseCount,
                                          double passRate,
                                          int positiveCaseCount,
                                          int evaluationK,
                                          int retrievalLimit,
                                          String retrievalStrategy,
                                          String retrievalStrategyDescription,
                                          String baselineRetrievalStrategy,
                                          String baselineRetrievalStrategyDescription,
                                          String relevanceMode,
                                          double hitAtK,
                                          double mrr,
                                          int baselinePassedCaseCount,
                                          double baselinePassRate,
                                          double baselineHitAtK,
                                          double baselineMrr,
                                          double hitAtKDelta,
                                          double mrrDelta,
                                          List<RagRetrievalStrategyEvaluationResponse> strategyResults,
                                          List<RagRetrievalEvaluationCaseResponse> cases) {
        this.totalCaseCount = totalCaseCount;
        this.passedCaseCount = passedCaseCount;
        this.passRate = passRate;
        this.positiveCaseCount = positiveCaseCount;
        this.evaluationK = evaluationK;
        this.retrievalLimit = retrievalLimit;
        this.retrievalStrategy = retrievalStrategy;
        this.retrievalStrategyDescription = retrievalStrategyDescription;
        this.baselineRetrievalStrategy = baselineRetrievalStrategy;
        this.baselineRetrievalStrategyDescription = baselineRetrievalStrategyDescription;
        this.relevanceMode = relevanceMode;
        this.hitAtK = hitAtK;
        this.mrr = mrr;
        this.baselinePassedCaseCount = baselinePassedCaseCount;
        this.baselinePassRate = baselinePassRate;
        this.baselineHitAtK = baselineHitAtK;
        this.baselineMrr = baselineMrr;
        this.hitAtKDelta = hitAtKDelta;
        this.mrrDelta = mrrDelta;
        this.strategyResults = strategyResults;
        this.cases = cases;
    }

    public int getTotalCaseCount() {
        return totalCaseCount;
    }

    public void setTotalCaseCount(int totalCaseCount) {
        this.totalCaseCount = totalCaseCount;
    }

    public int getPassedCaseCount() {
        return passedCaseCount;
    }

    public void setPassedCaseCount(int passedCaseCount) {
        this.passedCaseCount = passedCaseCount;
    }

    public double getPassRate() {
        return passRate;
    }

    public void setPassRate(double passRate) {
        this.passRate = passRate;
    }

    public int getPositiveCaseCount() {
        return positiveCaseCount;
    }

    public void setPositiveCaseCount(int positiveCaseCount) {
        this.positiveCaseCount = positiveCaseCount;
    }

    public int getEvaluationK() {
        return evaluationK;
    }

    public void setEvaluationK(int evaluationK) {
        this.evaluationK = evaluationK;
    }

    public int getRetrievalLimit() {
        return retrievalLimit;
    }

    public void setRetrievalLimit(int retrievalLimit) {
        this.retrievalLimit = retrievalLimit;
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

    public String getBaselineRetrievalStrategy() {
        return baselineRetrievalStrategy;
    }

    public void setBaselineRetrievalStrategy(String baselineRetrievalStrategy) {
        this.baselineRetrievalStrategy = baselineRetrievalStrategy;
    }

    public String getBaselineRetrievalStrategyDescription() {
        return baselineRetrievalStrategyDescription;
    }

    public void setBaselineRetrievalStrategyDescription(String baselineRetrievalStrategyDescription) {
        this.baselineRetrievalStrategyDescription = baselineRetrievalStrategyDescription;
    }

    public String getRelevanceMode() {
        return relevanceMode;
    }

    public void setRelevanceMode(String relevanceMode) {
        this.relevanceMode = relevanceMode;
    }

    public double getHitAtK() {
        return hitAtK;
    }

    public void setHitAtK(double hitAtK) {
        this.hitAtK = hitAtK;
    }

    public double getMrr() {
        return mrr;
    }

    public void setMrr(double mrr) {
        this.mrr = mrr;
    }

    public int getBaselinePassedCaseCount() {
        return baselinePassedCaseCount;
    }

    public void setBaselinePassedCaseCount(int baselinePassedCaseCount) {
        this.baselinePassedCaseCount = baselinePassedCaseCount;
    }

    public double getBaselinePassRate() {
        return baselinePassRate;
    }

    public void setBaselinePassRate(double baselinePassRate) {
        this.baselinePassRate = baselinePassRate;
    }

    public double getBaselineHitAtK() {
        return baselineHitAtK;
    }

    public void setBaselineHitAtK(double baselineHitAtK) {
        this.baselineHitAtK = baselineHitAtK;
    }

    public double getBaselineMrr() {
        return baselineMrr;
    }

    public void setBaselineMrr(double baselineMrr) {
        this.baselineMrr = baselineMrr;
    }

    public double getHitAtKDelta() {
        return hitAtKDelta;
    }

    public void setHitAtKDelta(double hitAtKDelta) {
        this.hitAtKDelta = hitAtKDelta;
    }

    public double getMrrDelta() {
        return mrrDelta;
    }

    public void setMrrDelta(double mrrDelta) {
        this.mrrDelta = mrrDelta;
    }

    public List<RagRetrievalStrategyEvaluationResponse> getStrategyResults() {
        return strategyResults;
    }

    public void setStrategyResults(List<RagRetrievalStrategyEvaluationResponse> strategyResults) {
        this.strategyResults = strategyResults;
    }

    public List<RagRetrievalEvaluationCaseResponse> getCases() {
        return cases;
    }

    public void setCases(List<RagRetrievalEvaluationCaseResponse> cases) {
        this.cases = cases;
    }
}
