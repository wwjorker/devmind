package com.devmind.module.search.strategy;

import com.devmind.module.search.vo.ChunkSearchResponse;

import java.util.List;

public interface RetrievalStrategy {

    String strategyName();

    String description();

    List<ChunkSearchResponse> retrieve(Long userId, List<String> keywords, Integer limit);
}
