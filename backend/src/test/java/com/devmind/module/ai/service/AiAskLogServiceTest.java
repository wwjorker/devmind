package com.devmind.module.ai.service;

import com.devmind.module.ai.entity.AiAskLog;
import com.devmind.module.ai.mapper.AiAskLogMapper;
import com.devmind.module.search.vo.ChunkSearchResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiAskLogServiceTest {

    @Test
    void saveFailureLogShouldPersistFailedStatusAndContext() {
        AiAskLogMapper askLogMapper = mock(AiAskLogMapper.class);
        when(askLogMapper.insert(any(AiAskLog.class))).thenAnswer(invocation -> {
            AiAskLog log = invocation.getArgument(0);
            log.setId(99L);
            return 1;
        });
        AiAskLogService askLogService = new AiAskLogService(askLogMapper);
        List<ChunkSearchResponse> chunks = List.of(
                new ChunkSearchResponse(10L, 1L, "Redis note", "bug_review", "redis", 0, "content", 20, 18),
                new ChunkSearchResponse(11L, 1L, "Redis note", "bug_review", "redis", 1, "content", 22, 12)
        );

        Long logId = askLogService.saveFailureLog(
                1L,
                "How to explain cache penetration?",
                "cache,penetration",
                "prompt preview",
                "DeepSeek request failed",
                "deepseek",
                false,
                chunks,
                123L
        );

        ArgumentCaptor<AiAskLog> captor = ArgumentCaptor.forClass(AiAskLog.class);
        verify(askLogMapper).insert(captor.capture());
        AiAskLog savedLog = captor.getValue();

        assertThat(logId).isEqualTo(99L);
        assertThat(savedLog.getStatus()).isZero();
        assertThat(savedLog.getAnswer()).isEqualTo("LLM request failed: DeepSeek request failed");
        assertThat(savedLog.getModelProvider()).isEqualTo("deepseek");
        assertThat(savedLog.getMock()).isFalse();
        assertThat(savedLog.getPromptPreview()).isEqualTo("prompt preview");
        assertThat(savedLog.getRetrievedChunkCount()).isEqualTo(2);
        assertThat(savedLog.getRetrievedChunkIds()).isEqualTo("10,11");
        assertThat(savedLog.getElapsedMs()).isEqualTo(123L);
    }
}
