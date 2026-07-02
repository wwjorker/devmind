package com.devmind.module.search.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.devmind.module.document.entity.DocumentChunk;
import com.devmind.module.document.entity.KnowledgeDocument;
import com.devmind.module.document.mapper.DocumentChunkMapper;
import com.devmind.module.document.mapper.KnowledgeDocumentMapper;
import com.devmind.module.search.embedding.EmbeddingClient;
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
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ChunkVectorService {

    private static final Logger log = LoggerFactory.getLogger(ChunkVectorService.class);
    private static final int STATUS_ACTIVE = 1;
    private static final int STATUS_ARCHIVED = 0;
    private static final TypeReference<Map<String, Double>> VECTOR_TYPE = new TypeReference<>() {
    };

    private final DocumentChunkVectorMapper vectorMapper;
    private final DocumentChunkMapper chunkMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final EmbeddingClientRouter embeddingClientRouter;
    private final EmbeddingTextBuilder embeddingTextBuilder;
    private final ObjectMapper objectMapper;

    public ChunkVectorService(DocumentChunkVectorMapper vectorMapper,
                              DocumentChunkMapper chunkMapper,
                              KnowledgeDocumentMapper documentMapper,
                              EmbeddingClientRouter embeddingClientRouter,
                              EmbeddingTextBuilder embeddingTextBuilder,
                              ObjectMapper objectMapper) {
        this.vectorMapper = vectorMapper;
        this.chunkMapper = chunkMapper;
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
        // A content rebuild invalidates every vector for the old chunks, regardless of provider.
        QueryWrapper<DocumentChunkVector> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId)
                .eq("document_id", documentId)
                .eq("status", STATUS_ACTIVE);
        List<DocumentChunkVector> activeVectors = vectorMapper.selectList(queryWrapper);
        for (DocumentChunkVector vector : activeVectors) {
            vector.setStatus(STATUS_ARCHIVED);
            vectorMapper.updateById(vector);
        }
    }

    @Transactional
    public void backfillVectors(Long userId, String provider) {
        EmbeddingClient embeddingClient = embeddingClientRouter.clientFor(provider);
        List<DocumentChunk> chunks = listActiveChunks(userId);
        if (chunks.isEmpty()) {
            return;
        }

        Set<Long> existingActiveChunkIds = listExistingActiveChunkIds(userId, embeddingClient.providerName());
        Set<Long> documentIds = chunks.stream()
                .map(DocumentChunk::getDocumentId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, KnowledgeDocument> activeDocuments = findActiveDocuments(userId, documentIds);

        for (DocumentChunk chunk : chunks) {
            if (existingActiveChunkIds.contains(chunk.getId())) {
                continue;
            }
            KnowledgeDocument document = activeDocuments.get(chunk.getDocumentId());
            if (document == null) {
                continue;
            }
            Map<String, Double> vector = embeddingClient.embed(embeddingTextBuilder.buildForChunk(document, chunk));
            if (vector.isEmpty()) {
                continue;
            }
            saveVector(userId, document.getId(), chunk.getId(), embeddingClient.providerName(), vector);
        }
    }

    public List<DocumentChunkVector> listActiveVectors(Long userId, int limit) {
        return listActiveVectors(userId, embeddingClientRouter.providerName(), limit);
    }

    public List<DocumentChunkVector> listActiveVectors(Long userId, String provider, int limit) {
        QueryWrapper<DocumentChunkVector> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId)
                .eq("provider_name", provider)
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

    private List<DocumentChunk> listActiveChunks(Long userId) {
        LambdaQueryWrapper<DocumentChunk> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(DocumentChunk::getUserId, userId)
                .eq(DocumentChunk::getStatus, STATUS_ACTIVE)
                .orderByAsc(DocumentChunk::getDocumentId)
                .orderByAsc(DocumentChunk::getChunkIndex)
                .orderByAsc(DocumentChunk::getId);
        return chunkMapper.selectList(queryWrapper);
    }

    private Set<Long> listExistingActiveChunkIds(Long userId, String provider) {
        QueryWrapper<DocumentChunkVector> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId)
                .eq("provider_name", provider)
                .eq("status", STATUS_ACTIVE);
        return vectorMapper.selectList(queryWrapper).stream()
                .map(DocumentChunkVector::getChunkId)
                .collect(Collectors.toSet());
    }

    private Map<Long, KnowledgeDocument> findActiveDocuments(Long userId, Set<Long> documentIds) {
        if (documentIds.isEmpty()) {
            return Map.of();
        }
        LambdaQueryWrapper<KnowledgeDocument> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(KnowledgeDocument::getUserId, userId)
                .eq(KnowledgeDocument::getStatus, STATUS_ACTIVE)
                .in(KnowledgeDocument::getId, documentIds);
        return documentMapper.selectList(queryWrapper).stream()
                .collect(Collectors.toMap(KnowledgeDocument::getId, Function.identity()));
    }

    private void saveVector(Long userId,
                            Long documentId,
                            Long chunkId,
                            String provider,
                            Map<String, Double> vector) {
        DocumentChunkVector existingVector = findVector(chunkId, provider);
        if (existingVector != null) {
            existingVector.setUserId(userId);
            existingVector.setDocumentId(documentId);
            existingVector.setVectorJson(encodeVector(vector));
            existingVector.setStatus(STATUS_ACTIVE);
            vectorMapper.updateById(existingVector);
            return;
        }

        DocumentChunkVector chunkVector = new DocumentChunkVector();
        chunkVector.setUserId(userId);
        chunkVector.setDocumentId(documentId);
        chunkVector.setChunkId(chunkId);
        chunkVector.setProviderName(provider);
        chunkVector.setVectorJson(encodeVector(vector));
        chunkVector.setStatus(STATUS_ACTIVE);
        vectorMapper.insert(chunkVector);
    }

    private DocumentChunkVector findVector(Long chunkId, String provider) {
        QueryWrapper<DocumentChunkVector> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("chunk_id", chunkId)
                .eq("provider_name", provider)
                .last("LIMIT 1");
        return vectorMapper.selectOne(queryWrapper);
    }

    private boolean isActive(Integer status) {
        return Integer.valueOf(STATUS_ACTIVE).equals(status);
    }
}
