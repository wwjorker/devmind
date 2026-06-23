package com.devmind.module.ai.service;

import com.devmind.module.ai.dto.AskRequest;
import com.devmind.module.ai.llm.LlmClientRouter;
import com.devmind.module.ai.llm.LlmRequest;
import com.devmind.module.ai.llm.LlmResponse;
import com.devmind.module.ai.vo.AskResponse;
import com.devmind.module.ai.vo.CitationResponse;
import com.devmind.module.search.service.ChunkSearchService;
import com.devmind.module.search.vo.ChunkSearchResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AiAskService {

    private static final int DEFAULT_RETRIEVAL_LIMIT = 3;

    private final ChunkSearchService chunkSearchService;
    private final AiAskLogService askLogService;
    private final PromptBuilderService promptBuilderService;
    private final RetrievalKeywordService retrievalKeywordService;
    private final LlmClientRouter llmClientRouter;

    public AiAskService(ChunkSearchService chunkSearchService,
                        AiAskLogService askLogService,
                        PromptBuilderService promptBuilderService,
                        RetrievalKeywordService retrievalKeywordService,
                        LlmClientRouter llmClientRouter) {
        this.chunkSearchService = chunkSearchService;
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

        List<ChunkSearchResponse> chunks = chunkSearchService.searchChunks(userId, retrievalKeywords, retrievalLimit);
        String promptPreview = promptBuilderService.buildPrompt(question, chunks);
        List<CitationResponse> citations = buildCitations(chunks);
        LlmResponse llmResponse;
        try {
            llmResponse = llmClientRouter.generate(new LlmRequest(question, promptPreview, chunks, citations));
        } catch (RuntimeException ex) {
            long elapsedMs = System.currentTimeMillis() - startTime;
            String modelProvider = llmClientRouter.getConfiguredProvider();
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
            throw ex;
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
}
