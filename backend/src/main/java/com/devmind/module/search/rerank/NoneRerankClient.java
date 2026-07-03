package com.devmind.module.search.rerank;

import com.devmind.module.search.vo.ChunkSearchResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NoneRerankClient implements RerankClient {

    @Override
    public String rerankName() {
        return "none";
    }

    @Override
    public List<ChunkSearchResponse> rerank(String query, List<ChunkSearchResponse> candidates, int topN) {
        if (candidates == null || candidates.isEmpty() || topN <= 0) {
            return List.of();
        }
        return candidates.stream()
                .limit(topN)
                .toList();
    }
}
