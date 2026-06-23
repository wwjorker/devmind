package com.devmind.module.ai.llm;

public class LlmResponse {

    private final String answer;
    private final String modelProvider;
    private final boolean mock;
    private final Integer promptTokens;
    private final Integer completionTokens;
    private final Integer totalTokens;

    public LlmResponse(String answer, String modelProvider, boolean mock) {
        this(answer, modelProvider, mock, null, null, null);
    }

    public LlmResponse(String answer,
                       String modelProvider,
                       boolean mock,
                       Integer promptTokens,
                       Integer completionTokens,
                       Integer totalTokens) {
        this.answer = answer;
        this.modelProvider = modelProvider;
        this.mock = mock;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
    }

    public String getAnswer() {
        return answer;
    }

    public String getModelProvider() {
        return modelProvider;
    }

    public boolean isMock() {
        return mock;
    }

    public Integer getPromptTokens() {
        return promptTokens;
    }

    public Integer getCompletionTokens() {
        return completionTokens;
    }

    public Integer getTotalTokens() {
        return totalTokens;
    }
}
