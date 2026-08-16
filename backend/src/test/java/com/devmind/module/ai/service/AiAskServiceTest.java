package com.devmind.module.ai.service;

import com.devmind.module.ai.dto.AskRequest;
import com.devmind.module.ai.llm.LlmClientRouter;
import com.devmind.module.ai.llm.LlmRequest;
import com.devmind.module.ai.llm.LlmResponse;
import com.devmind.module.ai.vo.AskResponse;
import com.devmind.module.ai.vo.CitationResponse;
import com.devmind.module.search.strategy.RetrievalStrategy;
import com.devmind.module.search.vo.ChunkSearchResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiAskServiceTest {

    @Test
    void askShouldReturnGroundedFallbackWhenNoChunksWereRetrieved() {
        RetrievalStrategy retrievalStrategy = mock(RetrievalStrategy.class);
        AiAskLogService askLogService = mock(AiAskLogService.class);
        LlmClientRouter llmClientRouter = mock(LlmClientRouter.class);
        when(retrievalStrategy.retrieve(eq(1L), anyList(), anyInt())).thenReturn(List.of());
        when(askLogService.saveSuccessLog(
                eq(1L),
                any(),
                any(),
                any(),
                any(),
                eq("knowledge-base-fallback"),
                eq(true),
                isNull(),
                isNull(),
                isNull(),
                anyList(),
                anyLong()
        )).thenReturn(88L);
        AiAskService aiAskService = new AiAskService(
                retrievalStrategy,
                askLogService,
                new PromptBuilderService(),
                new RetrievalKeywordService(true),
                llmClientRouter
        );
        AskRequest request = new AskRequest();
        request.setQuestion("What is Kubernetes pod eviction policy?");

        AskResponse response = aiAskService.ask(1L, request);

        assertThat(response.getLogId()).isEqualTo(88L);
        assertThat(response.getModelProvider()).isEqualTo("knowledge-base-fallback");
        assertThat(response.isMock()).isTrue();
        assertThat(response.getRetrievedChunks()).isEmpty();
        assertThat(response.getCitations()).isEmpty();
        assertThat(response.getAnswer()).contains("does not contain enough information");
        verify(llmClientRouter, never()).generate(any(LlmRequest.class));
    }

    @Test
    void askShouldReturnChineseFallbackWhenChineseQuestionHasNoChunks() {
        RetrievalStrategy retrievalStrategy = mock(RetrievalStrategy.class);
        AiAskLogService askLogService = mock(AiAskLogService.class);
        LlmClientRouter llmClientRouter = mock(LlmClientRouter.class);
        when(retrievalStrategy.retrieve(eq(1L), anyList(), anyInt())).thenReturn(List.of());
        when(askLogService.saveSuccessLog(
                eq(1L),
                any(),
                any(),
                any(),
                any(),
                eq("knowledge-base-fallback"),
                eq(true),
                isNull(),
                isNull(),
                isNull(),
                anyList(),
                anyLong()
        )).thenReturn(89L);
        AiAskService aiAskService = new AiAskService(
                retrievalStrategy,
                askLogService,
                new PromptBuilderService(),
                new RetrievalKeywordService(true),
                llmClientRouter
        );
        AskRequest request = new AskRequest();
        request.setQuestion("Kubernetes Pod 驱逐策略是什么？");

        AskResponse response = aiAskService.ask(1L, request);

        assertThat(response.getLogId()).isEqualTo(89L);
        assertThat(response.getAnswer()).contains("当前知识库没有足够信息");
        verify(llmClientRouter, never()).generate(any(LlmRequest.class));
    }

    @Test
    void askShouldSendFullMultiChunkPromptAndReturnMatchingVisibleCitations() {
        RetrievalStrategy retrievalStrategy = mock(RetrievalStrategy.class);
        AiAskLogService askLogService = mock(AiAskLogService.class);
        LlmClientRouter llmClientRouter = mock(LlmClientRouter.class);
        List<ChunkSearchResponse> chunks = List.of(
                longChunk(31L, "first"),
                longChunk(32L, "second"),
                longChunk(33L, "third")
        );
        when(retrievalStrategy.retrieve(eq(1L), anyList(), anyInt())).thenReturn(chunks);
        when(llmClientRouter.generate(any(LlmRequest.class))).thenReturn(
                new LlmResponse("grounded answer", "deepseek:test", false, 2100, 20, 2120)
        );
        when(askLogService.saveSuccessLog(
                eq(1L),
                any(),
                any(),
                any(),
                eq("grounded answer"),
                eq("deepseek:test"),
                eq(false),
                eq(2100),
                eq(20),
                eq(2120),
                eq(chunks),
                anyLong()
        )).thenReturn(91L);
        AiAskService aiAskService = new AiAskService(
                retrievalStrategy,
                askLogService,
                new PromptBuilderService(),
                new RetrievalKeywordService(true),
                llmClientRouter
        );
        AskRequest request = new AskRequest();
        request.setQuestion("Explain all retrieved evidence.");

        AskResponse response = aiAskService.ask(1L, request);

        ArgumentCaptor<LlmRequest> requestCaptor = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llmClientRouter).generate(requestCaptor.capture());
        LlmRequest llmRequest = requestCaptor.getValue();
        assertThat(llmRequest.getPrompt())
                .hasSizeGreaterThan(2000)
                .contains("[chunkId=31,")
                .contains("[chunkId=32,")
                .contains("[chunkId=33,");
        assertThat(response.getPromptPreview()).hasSize(2000).endsWith("...");
        assertThat(llmRequest.getPrompt())
                .startsWith(response.getPromptPreview().substring(0, response.getPromptPreview().length() - 3));
        assertThat(llmRequest.getRetrievedChunks()).containsExactlyElementsOf(chunks);
        assertThat(llmRequest.getCitations())
                .extracting(CitationResponse::getChunkId)
                .containsExactly(31L, 32L, 33L);
        assertThat(response.getCitations())
                .extracting(CitationResponse::getChunkId)
                .containsExactly(31L, 32L, 33L);
        verify(askLogService).saveSuccessLog(
                eq(1L),
                eq("Explain all retrieved evidence."),
                any(),
                eq(response.getPromptPreview()),
                eq("grounded answer"),
                eq("deepseek:test"),
                eq(false),
                eq(2100),
                eq(20),
                eq(2120),
                eq(chunks),
                anyLong()
        );
    }

    @Test
    void askShouldFallbackToMockWhenConfiguredProviderFails() {
        RetrievalStrategy retrievalStrategy = mock(RetrievalStrategy.class);
        AiAskLogService askLogService = mock(AiAskLogService.class);
        LlmClientRouter llmClientRouter = mock(LlmClientRouter.class);
        ChunkSearchResponse chunk = new ChunkSearchResponse(
                10L,
                20L,
                "Redis 缓存穿透复盘",
                "bug_review",
                "redis,cache",
                0,
                "缓存穿透可以通过缓存空值和限流缓解。",
                32,
                91
        );

        when(retrievalStrategy.retrieve(eq(1L), anyList(), anyInt())).thenReturn(List.of(chunk));
        when(llmClientRouter.getConfiguredProvider()).thenReturn("deepseek");
        when(llmClientRouter.generate(any(LlmRequest.class))).thenThrow(new RuntimeException("DeepSeek timeout"));
        when(llmClientRouter.generateFallbackFromConfiguredProvider(any(LlmRequest.class)))
                .thenReturn(new LlmResponse("这是降级后的本地回答。", "deepseek->mock-local", true));
        when(askLogService.saveSuccessLog(
                eq(1L),
                any(),
                any(),
                any(),
                any(),
                eq("deepseek->mock-local"),
                eq(true),
                isNull(),
                isNull(),
                isNull(),
                anyList(),
                anyLong()
        )).thenReturn(90L);

        AiAskService aiAskService = new AiAskService(
                retrievalStrategy,
                askLogService,
                new PromptBuilderService(),
                new RetrievalKeywordService(true),
                llmClientRouter
        );
        AskRequest request = new AskRequest();
        request.setQuestion("Redis 缓存穿透怎么处理？");

        AskResponse response = aiAskService.ask(1L, request);

        assertThat(response.getLogId()).isEqualTo(90L);
        assertThat(response.getModelProvider()).isEqualTo("deepseek->mock-local");
        assertThat(response.isMock()).isTrue();
        assertThat(response.getAnswer()).contains("降级后的本地回答");
        verify(askLogService).saveFailureLog(
                eq(1L),
                eq("Redis 缓存穿透怎么处理？"),
                any(),
                any(),
                eq("DeepSeek timeout"),
                eq("deepseek"),
                eq(false),
                eq(List.of(chunk)),
                anyLong()
        );
    }

    private ChunkSearchResponse longChunk(Long chunkId, String marker) {
        return new ChunkSearchResponse(
                chunkId,
                chunkId + 100,
                marker + " evidence",
                "bug_review",
                "test",
                0,
                marker + " " + "x".repeat(1000),
                1000,
                10
        );
    }
}
