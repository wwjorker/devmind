package com.devmind.module.ai.service;

import com.devmind.common.api.ResultCode;
import com.devmind.common.exception.BizException;
import com.devmind.module.ai.entity.RepairProposal;
import com.devmind.module.search.service.ChunkSearchService;
import com.devmind.module.search.vo.ChunkSearchResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class RegressionRunner {

    private static final int TARGET_LIMIT = 3;

    private final ChunkSearchService searchService;
    private final RetrievalKeywordService retrievalKeywordService;
    private final ObjectMapper objectMapper;

    public RegressionRunner(ChunkSearchService searchService,
                            RetrievalKeywordService retrievalKeywordService,
                            ObjectMapper objectMapper) {
        this.searchService = searchService;
        this.retrievalKeywordService = retrievalKeywordService;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RegressionResult runTargetRetrieval(Long userId, RepairProposal proposal) {
        String question = targetQuestion(proposal.getRegressionPlanJson());
        List<ChunkSearchResponse> matches = searchService.searchChunks(
                userId, retrievalKeywordService.resolveKeywords(question), TARGET_LIMIT);
        Integer rank = null;
        for (int index = 0; index < matches.size(); index++) {
            if (proposal.getTargetDocumentId().equals(matches.get(index).getDocumentId())) {
                rank = index + 1;
                break;
            }
        }
        boolean passed = rank != null;
        return new RegressionResult(
                passed,
                question,
                proposal.getTargetDocumentId(),
                rank,
                matches.stream().map(ChunkSearchResponse::getChunkId).toList(),
                passed
                        ? "target document retrieved within top " + TARGET_LIMIT
                        : "target document missing from top " + TARGET_LIMIT);
    }

    private String targetQuestion(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode value = root == null ? null : root.get("targetQuestion");
            if (value == null || !value.isTextual() || !StringUtils.hasText(value.textValue())) {
                throw new BizException(ResultCode.INTERNAL_ERROR,
                        "proposal regression plan has no target question");
            }
            return value.textValue().trim();
        } catch (JsonProcessingException ex) {
            throw new BizException(ResultCode.INTERNAL_ERROR,
                    "proposal regression plan is invalid");
        }
    }
}
