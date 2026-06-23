package com.devmind.module.ai.service;

import com.devmind.module.ai.dto.AskRequest;
import com.devmind.module.ai.llm.LlmClientRouter;
import com.devmind.module.ai.llm.LlmRequest;
import com.devmind.module.ai.vo.AskResponse;
import com.devmind.module.search.service.ChunkSearchService;
import org.junit.jupiter.api.Test;

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
        ChunkSearchService chunkSearchService = mock(ChunkSearchService.class);
        AiAskLogService askLogService = mock(AiAskLogService.class);
        LlmClientRouter llmClientRouter = mock(LlmClientRouter.class);
        when(chunkSearchService.searchChunks(eq(1L), anyList(), anyInt())).thenReturn(List.of());
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
                chunkSearchService,
                askLogService,
                new PromptBuilderService(),
                new RetrievalKeywordService(),
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
}
