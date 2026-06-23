package com.devmind.module.ai.vo;

import java.util.List;

public class EvaluationSummaryResponse {

    private long totalFeedbackCount;
    private long helpfulCount;
    private long badCaseCount;
    private double badCaseRate;
    private List<BadCaseSummaryResponse> recentBadCases;

    public EvaluationSummaryResponse() {
    }

    public EvaluationSummaryResponse(long totalFeedbackCount,
                                     long helpfulCount,
                                     long badCaseCount,
                                     double badCaseRate,
                                     List<BadCaseSummaryResponse> recentBadCases) {
        this.totalFeedbackCount = totalFeedbackCount;
        this.helpfulCount = helpfulCount;
        this.badCaseCount = badCaseCount;
        this.badCaseRate = badCaseRate;
        this.recentBadCases = recentBadCases;
    }

    public long getTotalFeedbackCount() {
        return totalFeedbackCount;
    }

    public void setTotalFeedbackCount(long totalFeedbackCount) {
        this.totalFeedbackCount = totalFeedbackCount;
    }

    public long getHelpfulCount() {
        return helpfulCount;
    }

    public void setHelpfulCount(long helpfulCount) {
        this.helpfulCount = helpfulCount;
    }

    public long getBadCaseCount() {
        return badCaseCount;
    }

    public void setBadCaseCount(long badCaseCount) {
        this.badCaseCount = badCaseCount;
    }

    public double getBadCaseRate() {
        return badCaseRate;
    }

    public void setBadCaseRate(double badCaseRate) {
        this.badCaseRate = badCaseRate;
    }

    public List<BadCaseSummaryResponse> getRecentBadCases() {
        return recentBadCases;
    }

    public void setRecentBadCases(List<BadCaseSummaryResponse> recentBadCases) {
        this.recentBadCases = recentBadCases;
    }
}
