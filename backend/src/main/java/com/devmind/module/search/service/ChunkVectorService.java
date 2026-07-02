package com.devmind.module.search.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.devmind.module.document.entity.DocumentChunk;
import com.devmind.module.document.entity.KnowledgeDocument;
import com.devmind.module.document.mapper.KnowledgeDocumentMapper;
import com.devmind.module.search.embedding.EmbeddingClientRouter;
import com.devmind.module.search.embedding.EmbeddingTextBuilder;
import com.devmind.module.search.entity.DocumentChunkVector;
import com.devmind.module.search.mapper.DocumentChunkVectorMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class ChunkVectorService {

    private static final Logger log = LoggerFactory.getLogger(ChunkVectorService.class);
    private static final int STATUS_ACTIVE = 1;
    private static final int STATUS_ARCHIVED = 0;
    private static final TypeReference<Map<String, Double>> VECTOR_TYPE = new TypeReference<>() {
    };

    private final DocumentChunkVectorMapper vectorMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final EmbeddingClientRouter embeddingClientRouter;
    private final EmbeddingTextBuilder embeddingTextBuilder;
    private final ObjectMapper objectMapper;

    public ChunkVectorService(DocumentChunkVectorMapper vectorMapper,
                              KnowledgeDocumentMapper documentMapper,
                              EmbeddingClientRouter embeddingClientRouter,
                              EmbeddingTextBuilder embeddingTextBuilder,
                              ObjectMapper objectMapper) {
        this.vectorMapper = vectorMapper;
        this.documentMapper = documentMapper;
        this.embeddingClientRouter = embeddingClientRouter;
        this.embeddingTextBuilder = embeddingTextBuilder;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void rebuildVectors(Long userId, Long documentId, List<DocumentChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }

        KnowledgeDocument document = documentMapper.selectById(documentId);
        if (document == null || !userId.equals(document.getUserId()) || !isActive(document.getStatus())) {
            log.warn("Skip chunk vector rebuild because document is unavailable, userId={}, documentId={}", userId, documentId);
            return;
        }

        for (DocumentChunk chunk : chunks) {
            Map<String, Double> vector = embeddingClientRouter.embed(embeddingTextBuilder.buildForChunk(document, chunk));
            if (vector.isEmpty()) {
                continue;
            }
            DocumentChunkVector chunkVector = new DocumentChunkVector();
            chunkVector.setUserId(userId);
            chunkVector.setDocumentId(documentId);
            chunkVector.setChunkId(chunk.getId());
            chunkVector.setProviderName(embeddingClientRouter.providerName());
            chunkVector.setVectorJson(encodeVector(vector));
            chunkVector.setStatus(STATUS_ACTIVE);
            vectorMapper.insert(chunkVector);
        }
    }

    @Transactional
    public void archiveByDocument(Long userId, Long documentId) {
        UpdateWrapper<DocumentChunkVector> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("user_id", userId)
                .eq("document_id", documentId)
                .eq("provider_name", embeddingClientRouter.providerName())
                .eq("status", STATUS_ACTIVE)
                .set("status", STATUS_ARCHIVED);
        vectorMapper.update(updateWrapper);
    }

    public List<DocumentChunkVector> listActiveVectors(Long userId, int limit) {
        QueryWrapper<DocumentChunkVector> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId)
                .eq("provider_name", embeddingClientRouter.providerName())
                .eq("status", STATUS_ACTIVE)
                .orderByDesc("updated_at")
                .orderByDesc("id")
                .last("LIMIT " + Math.max(1, limit));
        return vectorMapper.selectList(queryWrapper);
    }

    public Map<String, Double> decodeVector(String vectorJson) {
        try {
            return objectMapper.readValue(vectorJson, VECTOR_TYPE);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to decode chunk vector, provider={}", embeddingClientRouter.providerName(), ex);
            return Map.of();
        }
    }

    private String encodeVector(Map<String, Double> vector) {
        try {
            return objectMapper.writeValueAsString(vector);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to encode chunk vector", ex);
        }
    }

    private boolean isActive(Integer status) {
        return Integer.valueOf(STATUS_ACTIVE).equals(status);
    }
}
