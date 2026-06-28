package com.devmind.module.ai.vo;

import java.util.List;

public class RagRetrievalEvaluationResponse {

    private int totalCaseCount;
    private int passedCaseCount;
    private double passRate;
    private List<RagRetrievalEvaluationCaseResponse> cases;

    public RagRetrievalEvaluationResponse() {
    }

    public RagRetrievalEvaluationResponse(int totalCaseCount,
                                          int passedCaseCount,
                                          double passRate,
                                          List<RagRetrievalEvaluationCaseResponse> cases) {
        this.totalCaseCount = totalCaseCount;
        this.passedCaseCount = passedCaseCount;
        this.passRate = passRate;
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

    public List<RagRetrievalEvaluationCaseResponse> getCases() {
        return cases;
    }

    public void setCases(List<RagRetrievalEvaluationCaseResponse> cases) {
        this.cases = cases;
    }
}
