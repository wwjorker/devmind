package com.devmind.module.search.rerank;

import com.devmind.module.search.vo.ChunkSearchResponse;

import java.util.List;

public interface RerankClient {

    String rerankName();

    List<ChunkSearchResponse> rerank(String query, List<ChunkSearchResponse> candidates, int topN);
}
