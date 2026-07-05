package com.devmind.module.search.strategy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devmind.module.document.entity.DocumentChunk;
import com.devmind.module.document.entity.KnowledgeDocument;
import com.devmind.module.document.mapper.DocumentChunkMapper;
import com.devmind.module.document.mapper.KnowledgeDocumentMapper;
import com.devmind.module.search.embedding.EmbeddingClient;
import com.devmind.module.search.embedding.EmbeddingClientRouter;
import com.devmind.module.search.embedding.EmbeddingTextBuilder;
import com.devmind.module.search.entity.DocumentChunkVector;
import com.devmind.module.search.service.ChunkVectorService;
import com.devmind.module.search.vectorstore.DenseVectorCodec;
import com.devmind.module.search.vectorstore.PgVectorStore;
import com.devmind.module.search.vo.ChunkSearchResponse;
import org.springframework.beans.factory.ObjectProvider;
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
    // Upper bound for the brute-force cosine scan over persisted vectors. Vectors are
    // stored as JSON in MySQL, so similarity is computed in memory; this bound keeps a
    // single query cheap. Beyond this scale the right fix is ANN storage (e.g. pgvector),
    // not a bigger constant. Rows past the bound are cut by recency and become invisible
    // to the vector channel; keyword/FULLTEXT recall still covers them.
    private static final int VECTOR_CANDIDATE_LIMIT = 512;
    private static final int VECTOR_SCORE_WEIGHT = 100;
    private static final int RRF_K = 60;
    private static final int RRF_SCORE_WEIGHT = 10_000;

    // Only dense embeddings fit the fixed-dimension pgvector schema.
    private static final String REMOTE_DENSE_PROVIDER = "remote-dense";

    private final KeywordRetrievalStrategy keywordRetrievalStrategy;
    private final DocumentChunkMapper chunkMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final EmbeddingClientRouter embeddingClientRouter;
    private final EmbeddingTextBuilder embeddingTextBuilder;
    private final ChunkVectorService chunkVectorService;
    private final ObjectProvider<PgVectorStore> pgVectorStoreProvider;

    public HybridRetrievalStrategy(KeywordRetrievalStrategy keywordRetrievalStrategy,
                                   DocumentChunkMapper chunkMapper,
                                   KnowledgeDocumentMapper documentMapper,
                                   EmbeddingClientRouter embeddingClientRouter,
                                   EmbeddingTextBuilder embeddingTextBuilder,
                                   ChunkVectorService chunkVectorService,
                                   ObjectProvider<PgVectorStore> pgVectorStoreProvider) {
        this.keywordRetrievalStrategy = keywordRetrievalStrategy;
        this.chunkMapper = chunkMapper;
        this.documentMapper = documentMapper;
        this.embeddingClientRouter = embeddingClientRouter;
        this.embeddingTextBuilder = embeddingTextBuilder;
        this.chunkVectorService = chunkVectorService;
        this.pgVectorStoreProvider = pgVectorStoreProvider;
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
        EmbeddingClient currentClient = embeddingClientRouter.currentClient();
        // Serve the vector arm from pgvector only when the store is enabled AND the
        // active embedding is dense; the local sparse representation never fits it.
        boolean usePgStore = pgVectorStoreProvider.getIfAvailable() != null
                && REMOTE_DENSE_PROVIDER.equals(currentClient.providerName());
        return retrieveInternal(userId, keywords, limit, currentClient, true, usePgStore);
    }

    public List<ChunkSearchResponse> retrieveWithEmbeddingProvider(Long userId,
                                                                   List<String> keywords,
                                                                   Integer limit,
                                                                   String provider) {
        return retrieveInternal(userId, keywords, limit, embeddingClientRouter.clientFor(provider), false, false);
    }

    /**
     * Evaluation entry point that forces the vector arm through the pgvector store,
     * so the same gold-label cases can compare MySQL-JSON brute force vs HNSW serving.
     */
    public List<ChunkSearchResponse> retrieveWithEmbeddingProviderAndPgStore(Long userId,
                                                                             List<String> keywords,
                                                                             Integer limit,
                                                                             String provider) {
        if (pgVectorStoreProvider.getIfAvailable() == null) {
            throw new IllegalStateException("pgvector store is not enabled (devmind.vector-store.provider)");
        }
        return retrieveInternal(userId, keywords, limit, embeddingClientRouter.clientFor(provider), false, true);
    }

    private List<ChunkSearchResponse> retrieveInternal(Long userId,
                                                       List<String> keywords,
                                                       Integer limit,
                                                       EmbeddingClient embeddingClient,
                                                       boolean allowOnTheFlyFallback,
                                                       boolean usePgStore) {
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
        List<ChunkSearchResponse> vectorResults = usePgStore
                ? retrieveByPgVector(userId, normalizedKeywords, embeddingClient)
                : retrieveByEmbedding(userId, normalizedKeywords, embeddingClient, allowOnTheFlyFallback);

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

    /**
     * Vector arm served by pgvector: the ANN index returns the top candidates directly,
     * instead of scanning persisted rows and computing cosine in the JVM.
     */
    private List<ChunkSearchResponse> retrieveByPgVector(Long userId,
                                                         List<String> keywords,
                                                         EmbeddingClient embeddingClient) {
        PgVectorStore pgVectorStore = pgVectorStoreProvider.getIfAvailable();
        Map<String, Double> queryVector = embeddingClient.embed(embeddingTextBuilder.buildForQuery(keywords));
        if (queryVector.isEmpty() || queryVector.size() != pgVectorStore.dimension()) {
            return List.of();
        }
        List<PgVectorStore.ChunkVectorMatch> matches = pgVectorStore.searchSimilar(
                userId,
                embeddingClient.providerName(),
                DenseVectorCodec.toFloatArray(queryVector, pgVectorStore.dimension()),
                MAX_LIMIT
        );
        if (matches.isEmpty()) {
            return List.of();
        }

        Set<Long> chunkIds = matches.stream()
                .map(PgVectorStore.ChunkVectorMatch::chunkId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, DocumentChunk> chunks = findActiveChunks(userId, chunkIds);
        Set<Long> documentIds = chunks.values().stream()
                .map(DocumentChunk::getDocumentId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, KnowledgeDocument> documents = findActiveDocuments(userId, documentIds);

        List<ChunkSearchResponse> responses = new ArrayList<>();
        for (PgVectorStore.ChunkVectorMatch match : matches) {
            DocumentChunk chunk = chunks.get(match.chunkId());
            if (chunk == null) {
                // Stale index row (chunk archived in MySQL after a failed double-write
                // cleanup); the source of truth wins and the row is simply skipped.
                continue;
            }
            KnowledgeDocument document = documents.get(chunk.getDocumentId());
            if (document == null) {
                continue;
            }
            if (match.similarity() <= 0.0) {
                continue;
            }
            responses.add(toVectorResponse(chunk, document, match.similarity()));
        }
        return sortByVectorScore(responses);
    }

    private List<ChunkSearchResponse> retrieveByEmbedding(Long userId,
                                                          List<String> keywords,
                                                          EmbeddingClient embeddingClient,
                                                          boolean allowOnTheFlyFallback) {
        Map<String, Double> queryVector = embeddingClient.embed(embeddingTextBuilder.buildForQuery(keywords));
        if (queryVector.isEmpty()) {
            return List.of();
        }

        List<DocumentChunkVector> persistedVectors = chunkVectorService.listActiveVectors(
                userId,
                embeddingClient.providerName(),
                VECTOR_CANDIDATE_LIMIT
        );
        if (!persistedVectors.isEmpty()) {
            return retrieveByPersistedVector(userId, queryVector, persistedVectors, embeddingClient);
        }
        if (allowOnTheFlyFallback) {
            return retrieveByOnTheFlyVector(userId, queryVector, embeddingClient);
        }
        return List.of();
    }

    private List<ChunkSearchResponse> retrieveByPersistedVector(Long userId,
                                                                Map<String, Double> queryVector,
                                                                List<DocumentChunkVector> persistedVectors,
                                                                EmbeddingClient embeddingClient) {
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

    private List<ChunkSearchResponse> retrieveByOnTheFlyVector(Long userId,
                                                               Map<String, Double> queryVector,
                                                               EmbeddingClient embeddingClient) {
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
