package com.devmind.module.ai.service;

import com.devmind.common.api.ResultCode;
import com.devmind.common.exception.BizException;
import com.devmind.module.search.vo.ChunkSearchResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RetrievedEvidenceSnapshotCodec {

    private static final TypeReference<List<RetrievedEvidenceSnapshot>> SNAPSHOT_LIST_TYPE =
            new TypeReference<>() {
            };

    private final ObjectMapper objectMapper;
    private final PromptBuilderService promptBuilderService;

    public RetrievedEvidenceSnapshotCodec(ObjectMapper objectMapper,
                                          PromptBuilderService promptBuilderService) {
        this.objectMapper = objectMapper;
        this.promptBuilderService = promptBuilderService;
    }

    public String encode(List<ChunkSearchResponse> chunks) {
        List<RetrievedEvidenceSnapshot> snapshots = chunks.stream()
                .map(chunk -> new RetrievedEvidenceSnapshot(
                        chunk.getChunkId(),
                        chunk.getDocumentId(),
                        chunk.getDocumentVersionNo(),
                        chunk.getDocumentTitle(),
                        chunk.getSourceType(),
                        chunk.getTags(),
                        chunk.getChunkIndex(),
                        promptBuilderService.modelVisibleChunkContent(chunk.getContent()),
                        chunk.getScore()))
                .toList();
        try {
            return objectMapper.writeValueAsString(snapshots);
        } catch (JsonProcessingException ex) {
            throw new BizException(ResultCode.INTERNAL_ERROR,
                    "failed to serialize retrieved evidence snapshot");
        }
    }

    public List<RetrievedEvidenceSnapshot> decode(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return List.copyOf(objectMapper.readValue(json, SNAPSHOT_LIST_TYPE));
        } catch (JsonProcessingException ex) {
            throw new BizException(ResultCode.INTERNAL_ERROR,
                    "invalid retrieved evidence snapshot");
        }
    }
}
