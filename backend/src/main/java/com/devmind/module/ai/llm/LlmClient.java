package com.devmind.module.ai.llm;

public interface LlmClient {

    boolean supports(String provider);

    LlmResponse generate(LlmRequest request);
}
