package com.devmind.module.search.rerank;

import com.devmind.common.api.ResultCode;
import com.devmind.common.exception.BizException;
import com.devmind.module.ai.config.AiProperties;
import com.devmind.module.search.vo.ChunkSearchResponse;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
public class RemoteRerankClient implements RerankClient {

    private static final Logger log = LoggerFactory.getLogger(RemoteRerankClient.class);

    private final AiProperties aiProperties;
    private final RestClient.Builder restClientBuilder;

    @Autowired
    public RemoteRerankClient(AiProperties aiProperties, RestClient.Builder restClientBuilder) {
        this.aiProperties = aiProperties;
        this.restClientBuilder = restClientBuilder;
    }

    public RemoteRerankClient(AiProperties aiProperties) {
        this(aiProperties, RestClient.builder());
    }

    @Override
    public String rerankName() {
        return "remote-rerank";
    }

    @Override
    public List<ChunkSearchResponse> rerank(String query, List<ChunkSearchResponse> candidates, int topN) {
        AiProperties.RerankProperties rerank = aiProperties.getRerank();
        AiProperties.RerankRemoteProperties remote = rerank == null ? null : rerank.getRemote();
        if (remote == null
                || !StringUtils.hasText(remote.getApiKey())
                || !StringUtils.hasText(remote.getBaseUrl())
                || !StringUtils.hasText(remote.getModel())) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "rerank provider is not configured");
        }
        if (candidates == null || candidates.isEmpty() || topN <= 0) {
            return List.of();
        }

        RestClient restClient = restClientBuilder.clone()
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + remote.getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        String endpoint = rerankEndpoint(remote.getBaseUrl());
        Map<String, Object> body = Map.of(
                "model", remote.getModel(),
                "query", query == null ? "" : query,
                "documents", candidates.stream()
                        .map(this::candidateDocument)
                        .toList(),
                "top_n", topN
        );

        try {
            JsonNode response = restClient.post()
                    .uri(endpoint)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            return reorderCandidates(response, candidates, topN);
        } catch (RestClientException | IllegalStateException ex) {
            log.warn("Remote rerank request failed. model={}, baseUrl={}",
                    remote.getModel(),
                    remote.getBaseUrl(),
                    ex);
            throw new BizException(ResultCode.INTERNAL_ERROR, "rerank request failed");
        }
    }

    String rerankEndpoint(String baseUrl) {
        return baseUrl.replaceAll("/+$", "") + "/rerank";
    }

    List<ChunkSearchResponse> reorderCandidates(JsonNode response, List<ChunkSearchResponse> candidates, int topN) {
        JsonNode resultsNode = response == null ? null : response.path("results");
        if (resultsNode == null || !resultsNode.isArray()) {
            throw new IllegalStateException("remote rerank response does not contain results");
        }

        return rankedCandidates(resultsNode, candidates, topN);
    }

    private List<ChunkSearchResponse> rankedCandidates(JsonNode resultsNode,
                                                       List<ChunkSearchResponse> candidates,
                                                       int topN) {
        List<RankedCandidate> rankedCandidates = new ArrayList<>();
        for (JsonNode resultNode : resultsNode) {
            rankedCandidates.add(toRankedCandidate(resultNode, candidates));
        }
        return rankedCandidates.stream()
                .sorted(Comparator.comparingDouble(RankedCandidate::score).reversed())
                .limit(topN)
                .map(RankedCandidate::candidate)
                .toList();
    }

    private RankedCandidate toRankedCandidate(JsonNode node, List<ChunkSearchResponse> candidates) {
        JsonNode indexNode = node.get("index");
        JsonNode scoreNode = node.get("relevance_score");
        if (indexNode == null || !indexNode.canConvertToInt() || scoreNode == null || !scoreNode.isNumber()) {
            throw new IllegalStateException("remote rerank result must contain numeric index and relevance_score");
        }
        int index = indexNode.asInt();
        if (index < 0 || index >= candidates.size()) {
            throw new IllegalStateException("remote rerank result index is out of range");
        }
        return new RankedCandidate(candidates.get(index), scoreNode.asDouble());
    }

    private String candidateDocument(ChunkSearchResponse candidate) {
        return candidate == null || candidate.getContent() == null ? "" : candidate.getContent();
    }

    private record RankedCandidate(ChunkSearchResponse candidate, double score) {
    }
}
