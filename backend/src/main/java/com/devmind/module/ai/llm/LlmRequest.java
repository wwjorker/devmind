package com.devmind.module.ai.llm;

import com.devmind.module.ai.vo.CitationResponse;
import com.devmind.module.search.vo.ChunkSearchResponse;

import java.util.List;

public class LlmRequest {

    private final String question;
    private final String prompt;
    private final List<ChunkSearchResponse> retrievedChunks;
    private final List<CitationResponse> citations;

    public LlmRequest(String question,
                      String prompt,
                      List<ChunkSearchResponse> retrievedChunks,
                      List<CitationResponse> citations) {
        this.question = question;
        this.prompt = prompt;
        this.retrievedChunks = retrievedChunks;
        this.citations = citations;
    }

    public String getQuestion() {
        return question;
    }

    public String getPrompt() {
        return prompt;
    }

    public List<ChunkSearchResponse> getRetrievedChunks() {
        return retrievedChunks;
    }

    public List<CitationResponse> getCitations() {
        return citations;
    }
}
