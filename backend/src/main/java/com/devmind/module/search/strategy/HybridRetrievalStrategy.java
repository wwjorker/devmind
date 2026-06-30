package com.devmind.module.search.strategy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devmind.module.document.entity.DocumentChunk;
import com.devmind.module.document.entity.KnowledgeDocument;
import com.devmind.module.document.mapper.DocumentChunkMapper;
import com.devmind.module.document.mapper.KnowledgeDocumentMapper;
import com.devmind.module.search.embedding.EmbeddingClient;
import com.devmind.module.search.embedding.EmbeddingTextBuilder;
import com.devmind.module.search.entity.DocumentChunkVector;
import com.devmind.module.search.service.ChunkVectorService;
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

    private static final String STRATEGY_NAME = "hybrid-keyword-local-sparse-vector-rrf-v1";
    private static final String DESCRIPTION = "Keyword/FULLTEXT baseline plus persisted local sparse-vector rerank fused by RRF";
    private static final int STATUS_ACTIVE = 1;
    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 20;
    private static final int VECTOR_CANDIDATE_LIMIT = 120;
    private static final int VECTOR_SCORE_WEIGHT = 100;
    private static final int RRF_K = 60;
    private static final int RRF_SCORE_WEIGHT = 10_000;

    private final KeywordRetrievalStrategy keywordRetrievalStrategy;
    private final DocumentChunkMapper chunkMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final EmbeddingClient embeddingClient;
    private final EmbeddingTextBuilder embeddingTextBuilder;
    private final ChunkVectorService chunkVectorService;

    public HybridRetrievalStrategy(KeywordRetrievalStrategy keywordRetrievalStrategy,
                                   DocumentChunkMapper chunkMapper,
                                   KnowledgeDocumentMapper documentMapper,
                                   EmbeddingClient embeddingClient,
                                   EmbeddingTextBuilder embeddingTextBuilder,
                                   ChunkVectorService chunkVectorService) {
        this.keywordRetrievalStrategy = keywordRetrievalStrategy;
        this.chunkMapper = chunkMapper;
        this.documentMapper = documentMapper;
        this.embeddingClient = embeddingClient;
        this.embeddingTextBuilder = embeddingTextBuilder;
        this.chunkVectorService = chunkVectorService;
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
        Map<Long, Double> rrfScores = new HashMap<>();
        accumulateRrfScores(keywordResults, merged, rrfScores);
        accumulateRrfScores(vectorResults, merged, rrfScores);

        return merged.values().stream()
                .map(result -> copyWithScore(result, toRrfScore(rrfScores.get(result.getChunkId()))))
                .sorted(Comparator.comparing(ChunkSearchResponse::getScore).reversed()
                        .thenComparing(ChunkSearchResponse::getChunkId))
                .limit(safeLimit)
                .toList();
    }

    private List<ChunkSearchResponse> retrieveByLocalEmbedding(Long userId, List<String> keywords) {
        Map<String, Double> queryVector = embeddingClient.embed(embeddingTextBuilder.buildForQuery(keywords));
        if (queryVector.isEmpty()) {
            return List.of();
        }

        List<DocumentChunkVector> persistedVectors = chunkVectorService.listActiveVectors(userId, VECTOR_CANDIDATE_LIMIT);
        if (!persistedVectors.isEmpty()) {
            return retrieveByPersistedSparseVector(userId, queryVector, persistedVectors);
        }
        return retrieveByOnTheFlySparseVector(userId, queryVector);
    }

    private List<ChunkSearchResponse> retrieveByPersistedSparseVector(Long userId,
                                                                      Map<String, Double> queryVector,
                                                                      List<DocumentChunkVector> persistedVectors) {
        Set<Long> chunkIds = persistedVectors.stream()
                .map(DocumentChunkVector::getChunkId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, DocumentChunk> chunks = findActiveChunks(userId, chunkIds);
        Set<Long> documentIds = chunks.values().stream()
                .map(DocumentChunk::getDocumentId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, KnowledgeDocument> documents = findActiveDocuments(userId, documentIds);

        List<ChunkSearchResponse> responses = new ArrayList<>();
        for (DocumentChunkVector persistedVector : persistedVectors) {
            DocumentChunk chunk = chunks.get(persistedVector.getChunkId());
            if (chunk == null) {
                continue;
            }
            KnowledgeDocument document = documents.get(chunk.getDocumentId());
            if (document == null) {
                continue;
            }
            double similarity = embeddingClient.cosineSimilarity(
                    queryVector,
                    chunkVectorService.decodeVector(persistedVector.getVectorJson())
            );
            if (similarity <= 0.0) {
                continue;
            }
            responses.add(toVectorResponse(chunk, document, similarity));
        }

        return sortByVectorScore(responses);
    }

    private List<ChunkSearchResponse> retrieveByOnTheFlySparseVector(Long userId, Map<String, Double> queryVector) {
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
            String embeddingText = embeddingTextBuilder.buildForChunk(document, chunk);
            double similarity = embeddingClient.cosineSimilarity(queryVector, embeddingClient.embed(embeddingText));
            if (similarity <= 0.0) {
                continue;
            }
            responses.add(toVectorResponse(chunk, document, similarity));
        }

        return sortByVectorScore(responses);
    }

    private List<ChunkSearchResponse> sortByVectorScore(List<ChunkSearchResponse> responses) {
        responses.sort(Comparator.comparingInt(ChunkSearchResponse::getScore).reversed());
        return responses;
    }

    private Map<Long, DocumentChunk> findActiveChunks(Long userId, Set<Long> chunkIds) {
        if (chunkIds.isEmpty()) {
            return Map.of();
        }
        LambdaQueryWrapper<DocumentChunk> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(DocumentChunk::getUserId, userId)
                .eq(DocumentChunk::getStatus, STATUS_ACTIVE)
                .in(DocumentChunk::getId, chunkIds);
        return chunkMapper.selectList(queryWrapper).stream()
                .collect(Collectors.toMap(DocumentChunk::getId, Function.identity()));
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

    private ChunkSearchResponse toVectorResponse(DocumentChunk chunk,
                                                 KnowledgeDocument document,
                                                 double similarity) {
        return new ChunkSearchResponse(
                chunk.getId(),
                chunk.getDocumentId(),
                document.getTitle(),
                document.getSourceType(),
                document.getTags(),
                chunk.getChunkIndex(),
                chunk.getContent(),
                chunk.getTokenCount(),
                Math.max(1, (int) Math.round(similarity * VECTOR_SCORE_WEIGHT))
        );
    }

    private void accumulateRrfScores(List<ChunkSearchResponse> results,
                                     Map<Long, ChunkSearchResponse> merged,
                                     Map<Long, Double> rrfScores) {
        for (int index = 0; index < results.size(); index++) {
            ChunkSearchResponse result = results.get(index);
            merged.putIfAbsent(result.getChunkId(), copyWithScore(result, safeScore(result.getScore())));
            rrfScores.merge(result.getChunkId(), reciprocalRank(index), Double::sum);
        }
    }

    private double reciprocalRank(int zeroBasedRank) {
        return 1.0 / (RRF_K + zeroBasedRank + 1);
    }

    private int toRrfScore(Double score) {
        return Math.max(1, (int) Math.round((score == null ? 0.0 : score) * RRF_SCORE_WEIGHT));
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
