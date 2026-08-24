package com.devmind.module.ai.service;

import java.util.List;

public record RegressionResult(
        boolean passed,
        String targetQuestion,
        Long targetDocumentId,
        Integer targetRank,
        List<Long> matchedChunkIds,
        String detail
) {
    public RegressionResult {
        matchedChunkIds = matchedChunkIds == null ? List.of() : List.copyOf(matchedChunkIds);
    }
}
