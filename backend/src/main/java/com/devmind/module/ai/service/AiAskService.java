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
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiAskService {

    private static final int DEFAULT_RETRIEVAL_LIMIT = 3;
    private static final Pattern ENGLISH_TOKEN_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_+#.-]*");

    private final ChunkSearchService chunkSearchService;
    private final AiAskLogService askLogService;
    private final PromptBuilderService promptBuilderService;
    private final LlmClientRouter llmClientRouter;

    public AiAskService(ChunkSearchService chunkSearchService,
                        AiAskLogService askLogService,
                        PromptBuilderService promptBuilderService,
                        LlmClientRouter llmClientRouter) {
        this.chunkSearchService = chunkSearchService;
        this.askLogService = askLogService;
        this.promptBuilderService = promptBuilderService;
        this.llmClientRouter = llmClientRouter;
    }

    public AskResponse ask(Long userId, AskRequest request) {
        long startTime = System.currentTimeMillis();
        String question = request.getQuestion().trim();
        String retrievalKeyword = resolveRetrievalKeyword(question);
        Integer retrievalLimit = request.getRetrievalLimit() == null
                ? DEFAULT_RETRIEVAL_LIMIT
                : request.getRetrievalLimit();

        List<ChunkSearchResponse> chunks = chunkSearchService.searchChunks(userId, retrievalKeyword, retrievalLimit);
        String promptPreview = promptBuilderService.buildPrompt(question, chunks);
        List<CitationResponse> citations = buildCitations(chunks);
        LlmResponse llmResponse = llmClientRouter.generate(new LlmRequest(question, promptPreview, chunks, citations));
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

    private String resolveRetrievalKeyword(String question) {
        String lowerQuestion = question.toLowerCase(Locale.ROOT);
        if (lowerQuestion.contains("redis")) {
            return "Redis";
        }
        if (lowerQuestion.contains("mysql")) {
            return "MySQL";
        }
        if (lowerQuestion.contains("jwt")) {
            return "JWT";
        }

        Matcher matcher = ENGLISH_TOKEN_PATTERN.matcher(question);
        String bestToken = null;
        while (matcher.find()) {
            String token = matcher.group();
            if (bestToken == null || token.length() > bestToken.length()) {
                bestToken = token;
            }
        }

        if (bestToken != null) {
            return bestToken;
        }
        return question;
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
}
