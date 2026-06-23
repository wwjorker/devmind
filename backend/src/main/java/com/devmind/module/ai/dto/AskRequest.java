package com.devmind.module.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AskRequest {

    @NotBlank(message = "question is required")
    @Size(max = 500, message = "question must be at most 500 characters")
    private String question;

    private Integer retrievalLimit;

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public Integer getRetrievalLimit() {
        return retrievalLimit;
    }

    public void setRetrievalLimit(Integer retrievalLimit) {
        this.retrievalLimit = retrievalLimit;
    }
}
