package com.devmind.module.ai.vo;

import java.time.LocalDateTime;

public class BadCaseSummaryResponse {

    private Long feedbackId;
    private Long askLogId;
    private String question;
    private String reason;
    private String expectedAnswer;
    private LocalDateTime createdAt;

    public BadCaseSummaryResponse() {
    }

    public BadCaseSummaryResponse(Long feedbackId,
                                  Long askLogId,
                                  String question,
                                  String reason,
                                  String expectedAnswer,
                                  LocalDateTime createdAt) {
        this.feedbackId = feedbackId;
        this.askLogId = askLogId;
        this.question = question;
        this.reason = reason;
        this.expectedAnswer = expectedAnswer;
        this.createdAt = createdAt;
    }

    public Long getFeedbackId() {
        return feedbackId;
    }

    public void setFeedbackId(Long feedbackId) {
        this.feedbackId = feedbackId;
    }

    public Long getAskLogId() {
        return askLogId;
    }

    public void setAskLogId(Long askLogId) {
        this.askLogId = askLogId;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getExpectedAnswer() {
        return expectedAnswer;
    }

    public void setExpectedAnswer(String expectedAnswer) {
        this.expectedAnswer = expectedAnswer;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
