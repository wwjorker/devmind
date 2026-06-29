package com.devmind.module.ai.service;

import com.devmind.module.ai.dto.AskRequest;
import com.devmind.module.ai.llm.LlmClientRouter;
import com.devmind.module.ai.llm.LlmRequest;
import com.devmind.module.ai.llm.LlmResponse;
import com.devmind.module.ai.vo.AskResponse;
import com.devmind.module.ai.vo.CitationResponse;
import com.devmind.module.search.strategy.RetrievalStrategy;
import com.devmind.module.search.vo.ChunkSearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AiAskService {

    private static final Logger log = LoggerFactory.getLogger(AiAskService.class);
    private static final int DEFAULT_RETRIEVAL_LIMIT = 3;
    private static final String NO_CONTEXT_PROVIDER = "knowledge-base-fallback";
    private static final String NO_CONTEXT_ANSWER_EN = """
            The knowledge base does not contain enough information to answer this question.
            Please add relevant notes or refine the question, then ask again.
            """;
    private static final String NO_CONTEXT_ANSWER_CN = """
            当前知识库没有足够信息回答这个问题。
            请先添加相关笔记，或者调整问题后再提问。
            """;

    private final RetrievalStrategy retrievalStrategy;
    private final AiAskLogService askLogService;
    private final PromptBuilderService promptBuilderService;
    private final RetrievalKeywordService retrievalKeywordService;
    private final LlmClientRouter llmClientRouter;

    public AiAskService(RetrievalStrategy retrievalStrategy,
                        AiAskLogService askLogService,
                        PromptBuilderService promptBuilderService,
                        RetrievalKeywordService retrievalKeywordService,
                        LlmClientRouter llmClientRouter) {
        this.retrievalStrategy = retrievalStrategy;
        this.askLogService = askLogService;
        this.promptBuilderService = promptBuilderService;
        this.retrievalKeywordService = retrievalKeywordService;
        this.llmClientRouter = llmClientRouter;
    }

    public AskResponse ask(Long userId, AskRequest request) {
        long startTime = System.currentTimeMillis();
        String question = request.getQuestion().trim();
        List<String> retrievalKeywords = retrievalKeywordService.resolveKeywords(question);
        String retrievalKeyword = retrievalKeywordService.toLogKeyword(retrievalKeywords);
        Integer retrievalLimit = request.getRetrievalLimit() == null
                ? DEFAULT_RETRIEVAL_LIMIT
                : request.getRetrievalLimit();

        List<ChunkSearchResponse> chunks = retrievalStrategy.retrieve(userId, retrievalKeywords, retrievalLimit);
        String promptPreview = promptBuilderService.buildPrompt(question, chunks);
        List<CitationResponse> citations = buildCitations(chunks);
        if (chunks.isEmpty()) {
            return buildNoContextResponse(
                    userId,
                    question,
                    retrievalKeyword,
                    promptPreview,
                    chunks,
                    citations,
                    System.currentTimeMillis() - startTime
            );
        }

        LlmRequest llmRequest = new LlmRequest(question, promptPreview, chunks, citations);
        LlmResponse llmResponse;
        try {
            llmResponse = llmClientRouter.generate(llmRequest);
        } catch (RuntimeException ex) {
            long elapsedMs = System.currentTimeMillis() - startTime;
            String modelProvider = llmClientRouter.getConfiguredProvider();
            log.warn("LLM provider failed, falling back to local mock provider. userId={}, provider={}, chunks={}, elapsedMs={}",
                    userId,
                    modelProvider,
                    chunks.size(),
                    elapsedMs,
                    ex);
            askLogService.saveFailureLog(
                    userId,
                    question,
                    retrievalKeyword,
                    promptPreview,
                    ex.getMessage(),
                    modelProvider,
                    isMockProvider(modelProvider),
                    chunks,
                    elapsedMs
            );
            llmResponse = llmClientRouter.generateFallbackFromConfiguredProvider(llmRequest);
        }
        String answer = llmResponse.getAnswer();
        String modelProvider = llmResponse.getModelProvider();
        boolean mock = llmResponse.isMock();
        long elapsedMs = System.currentTimeMillis() - startTime;
        Long logId = askLogService.saveSuccessLog(
                userId,
                question,
                retrievalKeyword,
                promptPreview,
                answer,
                modelProvider,
                mock,
                llmResponse.getPromptTokens(),
                llmResponse.getCompletionTokens(),
                llmResponse.getTotalTokens(),
                chunks,
                elapsedMs
        );

        return new AskResponse(
                logId,
                question,
                retrievalKeyword,
                promptPreview,
                answer,
                modelProvider,
                mock,
                llmResponse.getPromptTokens(),
                llmResponse.getCompletionTokens(),
                llmResponse.getTotalTokens(),
                chunks,
                citations
        );
    }

    private AskResponse buildNoContextResponse(Long userId,
                                               String question,
                                               String retrievalKeyword,
                                               String promptPreview,
                                               List<ChunkSearchResponse> chunks,
                                               List<CitationResponse> citations,
                                               long elapsedMs) {
        String answer = noContextAnswer(question);
        Long logId = askLogService.saveSuccessLog(
                userId,
                question,
                retrievalKeyword,
                promptPreview,
                answer,
                NO_CONTEXT_PROVIDER,
                true,
                null,
                null,
                null,
                chunks,
                elapsedMs
        );

        return new AskResponse(
                logId,
                question,
                retrievalKeyword,
                promptPreview,
                answer,
                NO_CONTEXT_PROVIDER,
                true,
                null,
                null,
                null,
                chunks,
                citations
        );
    }

    private List<CitationResponse> buildCitations(List<ChunkSearchResponse> chunks) {
        return chunks.stream()
                .map(chunk -> new CitationResponse(
                        chunk.getChunkId(),
                        chunk.getDocumentId(),
                        chunk.getDocumentTitle(),
                        chunk.getChunkIndex(),
                        chunk.getScore()
                ))
                .toList();
    }

    private boolean isMockProvider(String modelProvider) {
        return "mock".equalsIgnoreCase(modelProvider);
    }

    private String noContextAnswer(String question) {
        return containsChinese(question) ? NO_CONTEXT_ANSWER_CN : NO_CONTEXT_ANSWER_EN;
    }

    private boolean containsChinese(String text) {
        if (text == null) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            Character.UnicodeScript script = Character.UnicodeScript.of(text.charAt(i));
            if (script == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }
}
