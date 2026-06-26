package com.devmind.module.ai.service;

import com.devmind.module.ai.entity.AiAskLog;
import com.devmind.module.ai.mapper.AiAskLogMapper;
import com.devmind.module.ai.vo.RagEvaluationDatasetResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RagEvaluationDatasetServiceTest {

    @Test
    void datasetShouldReturnStaticCasesWhenNoAskLogsExist() {
        AiAskLogMapper askLogMapper = mock(AiAskLogMapper.class);
        when(askLogMapper.selectList(any())).thenReturn(List.of());
        RagEvaluationDatasetService service = new RagEvaluationDatasetService(askLogMapper);

        RagEvaluationDatasetResponse response = service.dataset(1L);

        assertThat(response.getTotalCaseCount()).isEqualTo(8);
        assertThat(response.getCoveredCaseCount()).isZero();
        assertThat(response.getCoverageRate()).isZero();
        assertThat(response.getCases())
                .extracting("caseId")
                .contains("redis-cache-penetration-basic", "unknown-kubernetes-fallback");
    }

    @Test
    void datasetShouldMarkCaseCoveredByLatestMatchingAskLog() {
        AiAskLogMapper askLogMapper = mock(AiAskLogMapper.class);
        AiAskLog log = new AiAskLog();
        log.setId(42L);
        log.setQuestion("面试中应该如何解释 Redis 缓存穿透？");
        log.setStatus(1);
        log.setRetrievedChunkCount(3);
        log.setRetrievedChunkIds("3,4,7");
        log.setCreatedAt(LocalDateTime.of(2026, 6, 25, 10, 0));
        when(askLogMapper.selectList(any())).thenReturn(List.of(log));
        RagEvaluationDatasetService service = new RagEvaluationDatasetService(askLogMapper);

        RagEvaluationDatasetResponse response = service.dataset(1L);

        assertThat(response.getCoveredCaseCount()).isEqualTo(1);
        assertThat(response.getCoverageRate()).isEqualTo(0.125);
        assertThat(response.getCases())
                .filteredOn(caseResponse -> "redis-cache-penetration-basic".equals(caseResponse.getCaseId()))
                .singleElement()
                .satisfies(caseResponse -> {
                    assertThat(caseResponse.getCovered()).isTrue();
                    assertThat(caseResponse.getLastAskLogId()).isEqualTo(42L);
                    assertThat(caseResponse.getLastRetrievedChunkCount()).isEqualTo(3);
                    assertThat(caseResponse.getLastRetrievedChunkIds()).isEqualTo("3,4,7");
                });
    }
}
