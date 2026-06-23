package com.devmind.module.ai.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class AskFeedbackRequest {

    @NotNull(message = "helpful is required")
    private Boolean helpful;

    @Size(max = 500, message = "reason must be at most 500 characters")
    private String reason;

    @Size(max = 3000, message = "expectedAnswer must be at most 3000 characters")
    private String expectedAnswer;

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
}
