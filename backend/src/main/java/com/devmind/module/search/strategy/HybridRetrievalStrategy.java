package com.devmind.module.search.strategy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devmind.module.document.entity.DocumentChunk;
import com.devmind.module.document.entity.KnowledgeDocument;
import com.devmind.module.document.mapper.DocumentChunkMapper;
import com.devmind.module.document.mapper.KnowledgeDocumentMapper;
import com.devmind.module.search.embedding.EmbeddingClient;
import com.devmind.module.search.vo.ChunkSearchResponse;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Primary
@Service
public class HybridRetrievalStrategy implements RetrievalStrategy {

    private static final String STRATEGY_NAME = "hybrid-keyword-local-embedding-v1";
    private static final String DESCRIPTION = "Keyword/FULLTEXT baseline plus local embedding similarity rerank";
    private static final int STATUS_ACTIVE = 1;
    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 20;
    private static final int VECTOR_CANDIDATE_LIMIT = 120;
    private static final int VECTOR_SCORE_WEIGHT = 100;

    private final KeywordRetrievalStrategy keywordRetrievalStrategy;
    private final DocumentChunkMapper chunkMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final EmbeddingClient embeddingClient;

    public HybridRetrievalStrategy(KeywordRetrievalStrategy keywordRetrievalStrategy,
                                   DocumentChunkMapper chunkMapper,
                                   KnowledgeDocumentMapper documentMapper,
                                   EmbeddingClient embeddingClient) {
        this.keywordRetrievalStrategy = keywordRetrievalStrategy;
        this.chunkMapper = chunkMapper;
        this.documentMapper = documentMapper;
        this.embeddingClient = embeddingClient;
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
        int safeLimit = resolveLimit(limit);
        List<String> normalizedKeywords = normalizeKeywords(keywords);
        if (normalizedKeywords.isEmpty()) {
            return keywordRetrievalStrategy.retrieve(userId, keywords, limit);
        }

        List<ChunkSearchResponse> keywordResults = keywordRetrievalStrategy.retrieve(
                userId,
                normalizedKeywords,
                Math.min(safeLimit * 3, MAX_LIMIT)
        );
        List<ChunkSearchResponse> vectorResults = retrieveByLocalEmbedding(userId, normalizedKeywords);

        Map<Long, ChunkSearchResponse> merged = new HashMap<>();
        for (ChunkSearchResponse result : keywordResults) {
            merged.put(result.getChunkId(), copyWithScore(result, safeScore(result.getScore())));
        }
        for (ChunkSearchResponse result : vectorResults) {
            merged.merge(
                    result.getChunkId(),
                    result,
                    (left, right) -> {
                        left.setScore(safeScore(left.getScore()) + safeScore(right.getScore()));
                        return left;
                    }
            );
        }

        return merged.values().stream()
                .sorted(Comparator.comparing(ChunkSearchResponse::getScore).reversed()
                        .thenComparing(ChunkSearchResponse::getChunkId))
                .limit(safeLimit)
                .toList();
    }

    private List<ChunkSearchResponse> retrieveByLocalEmbedding(Long userId, List<String> keywords) {
        Map<String, Double> queryVector = embeddingClient.embed(String.join(" ", keywords));
        if (queryVector.isEmpty()) {
            return List.of();
        }

        LambdaQueryWrapper<DocumentChunk> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(DocumentChunk::getUserId, userId)
                .eq(DocumentChunk::getStatus, STATUS_ACTIVE)
                .orderByDesc(DocumentChunk::getUpdatedAt)
                .orderByDesc(DocumentChunk::getId)
                .last("LIMIT " + VECTOR_CANDIDATE_LIMIT);

        List<DocumentChunk> chunks = chunkMapper.selectList(queryWrapper);
        if (chunks.isEmpty()) {
            return List.of();
        }

        Set<Long> documentIds = chunks.stream()
                .map(DocumentChunk::getDocumentId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, KnowledgeDocument> documents = findActiveDocuments(userId, documentIds);

        List<ChunkSearchResponse> responses = new ArrayList<>();
        for (DocumentChunk chunk : chunks) {
            KnowledgeDocument document = documents.get(chunk.getDocumentId());
            if (document == null) {
                continue;
            }
            String embeddingText = safeText(document.getTitle()) + " "
                    + safeText(document.getTags()) + " "
                    + safeText(chunk.getContent());
            double similarity = embeddingClient.cosineSimilarity(queryVector, embeddingClient.embed(embeddingText));
            if (similarity <= 0.0) {
                continue;
            }
            responses.add(new ChunkSearchResponse(
                    chunk.getId(),
                    chunk.getDocumentId(),
                    document.getTitle(),
                    document.getSourceType(),
                    document.getTags(),
                    chunk.getChunkIndex(),
                    chunk.getContent(),
                    chunk.getTokenCount(),
                    Math.max(1, (int) Math.round(similarity * VECTOR_SCORE_WEIGHT))
            ));
        }

        return responses;
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

    private ChunkSearchResponse copyWithScore(ChunkSearchResponse source, int score) {
        return new ChunkSearchResponse(
                source.getChunkId(),
                source.getDocumentId(),
                source.getDocumentTitle(),
                source.getSourceType(),
                source.getTags(),
                source.getChunkIndex(),
                source.getContent(),
                source.getTokenCount(),
                score
        );
    }

    private int resolveLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.min(Math.max(limit, 1), MAX_LIMIT);
    }

    private int safeScore(Integer score) {
        return score == null ? 0 : Math.max(score, 0);
    }

    private String safeText(String text) {
        return text == null ? "" : text;
    }

    private List<String> normalizeKeywords(List<String> keywords) {
        if (keywords == null) {
            return List.of();
        }
        return keywords.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(LinkedHashSet::new),
                        List::copyOf
                ));
    }
}
