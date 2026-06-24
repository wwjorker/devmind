package com.devmind.module.ai.vo;

import java.util.List;

public class RagEvaluationDatasetResponse {

    private int totalCaseCount;
    private int coveredCaseCount;
    private double coverageRate;
    private List<RagEvaluationCaseResponse> cases;

    public RagEvaluationDatasetResponse() {
    }

    public RagEvaluationDatasetResponse(int totalCaseCount,
                                        int coveredCaseCount,
                                        double coverageRate,
                                        List<RagEvaluationCaseResponse> cases) {
        this.totalCaseCount = totalCaseCount;
        this.coveredCaseCount = coveredCaseCount;
        this.coverageRate = coverageRate;
        this.cases = cases;
    }

    public int getTotalCaseCount() {
        return totalCaseCount;
    }

    public void setTotalCaseCount(int totalCaseCount) {
        this.totalCaseCount = totalCaseCount;
    }

    public int getCoveredCaseCount() {
        return coveredCaseCount;
    }

    public void setCoveredCaseCount(int coveredCaseCount) {
        this.coveredCaseCount = coveredCaseCount;
    }

    public double getCoverageRate() {
        return coverageRate;
    }

    public void setCoverageRate(double coverageRate) {
        this.coverageRate = coverageRate;
    }

    public List<RagEvaluationCaseResponse> getCases() {
        return cases;
    }

    public void setCases(List<RagEvaluationCaseResponse> cases) {
        this.cases = cases;
    }
}
