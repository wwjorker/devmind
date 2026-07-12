package com.devmind.module.ai.controller;

import com.devmind.common.api.Result;
import com.devmind.common.api.ResultCode;
import com.devmind.common.exception.BizException;
import com.devmind.common.security.AuthenticatedUser;
import com.devmind.common.ratelimit.AiAskRateLimiter;
import com.devmind.module.ai.dto.AskRequest;
import com.devmind.module.ai.service.AiAskFeedbackService;
import com.devmind.module.ai.service.AiAskLogService;
import com.devmind.module.ai.service.AiAskService;
import com.devmind.module.ai.service.RagEvaluationDatasetService;
import com.devmind.module.search.service.ChunkVectorService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AiAskControllerTest {

    @Test
    void backfillEmbeddingShouldUseCurrentUserScope() {
        ChunkVectorService chunkVectorService = mock(ChunkVectorService.class);
        AiAskController controller = controller(chunkVectorService);

        Result<Void> result = controller.backfillEmbedding(new AuthenticatedUser(7L, "alice"), "local-sparse-vector");

        assertThat(result.getCode()).isEqualTo(ResultCode.SUCCESS.getCode());
        verify(chunkVectorService).backfillVectors(7L, "local-sparse-vector");
    }

    @Test
    void backfillEmbeddingShouldFailFastWhenRemoteDenseIsNotConfigured() {
        ChunkVectorService chunkVectorService = mock(ChunkVectorService.class);
        doThrow(new BizException(ResultCode.INTERNAL_ERROR, "external embedding provider is not configured"))
                .when(chunkVectorService)
                .backfillVectors(7L, "remote-dense");
        AiAskController controller = controller(chunkVectorService);

        assertThatThrownBy(() -> controller.backfillEmbedding(new AuthenticatedUser(7L, "alice"), "remote-dense"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("external embedding provider is not configured");
    }

    @Test
    void askShouldApplyRateLimitForCurrentUser() {
        AiAskRateLimiter rateLimiter = mock(AiAskRateLimiter.class);
        AiAskService aiAskService = mock(AiAskService.class);
        AiAskController controller = controller(mock(ChunkVectorService.class), aiAskService, rateLimiter);
        AskRequest request = new AskRequest();
        request.setQuestion("What is RRF?");

        controller.ask(new AuthenticatedUser(7L, "alice"), request);

        verify(rateLimiter).checkAllowed(7L);
        verify(aiAskService).ask(7L, request);
    }

    private AiAskController controller(ChunkVectorService chunkVectorService) {
        return controller(chunkVectorService, mock(AiAskService.class), mock(AiAskRateLimiter.class));
    }

    private AiAskController controller(ChunkVectorService chunkVectorService,
                                       AiAskService aiAskService,
                                       AiAskRateLimiter rateLimiter) {
        return new AiAskController(
                aiAskService,
                mock(AiAskLogService.class),
                mock(AiAskFeedbackService.class),
                mock(RagEvaluationDatasetService.class),
                chunkVectorService,
                rateLimiter
        );
    }
}
