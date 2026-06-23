package com.devmind.module.ai.vo;

import java.time.LocalDateTime;

public class AskFeedbackResponse {

    private Long id;
    private Long askLogId;
    private Boolean helpful;
    private String reason;
    private String expectedAnswer;
    private Integer status;
    private LocalDateTime createdAt;

    public AskFeedbackResponse() {
    }

    public AskFeedbackResponse(Long id,
                               Long askLogId,
                               Boolean helpful,
                               String reason,
                               String expectedAnswer,
                               Integer status,
                               LocalDateTime createdAt) {
        this.id = id;
        this.askLogId = askLogId;
        this.helpful = helpful;
        this.reason = reason;
        this.expectedAnswer = expectedAnswer;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAskLogId() {
        return askLogId;
    }

    public void setAskLogId(Long askLogId) {
        this.askLogId = askLogId;
    }

    public Boolean getHelpful() {
        return helpful;
    }

    public void setHelpful(Boolean helpful) {
        this.helpful = helpful;
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
