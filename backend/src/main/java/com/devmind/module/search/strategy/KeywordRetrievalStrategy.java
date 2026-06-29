package com.devmind.module.search.strategy;

import com.devmind.module.search.service.ChunkSearchService;
import com.devmind.module.search.vo.ChunkSearchResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class KeywordRetrievalStrategy implements RetrievalStrategy {

    public static final String STRATEGY_NAME = "mysql-fulltext-keyword-v1";
    public static final String DESCRIPTION = "MySQL FULLTEXT plus multi-keyword metadata scoring";

    private final ChunkSearchService chunkSearchService;

    public KeywordRetrievalStrategy(ChunkSearchService chunkSearchService) {
        this.chunkSearchService = chunkSearchService;
    }

    @Override
    public String strategyName() {
        return STRATEGY_NAME;
    }

    @Override
    public String description() {
        return DESCRIPTION;
    }

    @Override
    public List<ChunkSearchResponse> retrieve(Long userId, List<String> keywords, Integer limit) {
        return chunkSearchService.searchChunks(userId, keywords, limit);
    }
}
