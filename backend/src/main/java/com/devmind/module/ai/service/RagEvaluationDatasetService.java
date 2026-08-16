package com.devmind.module.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devmind.module.ai.entity.AiAskLog;
import com.devmind.module.ai.mapper.AiAskLogMapper;
import com.devmind.module.ai.service.RagEvaluationDatasetCatalog.EvaluationCaseDefinition;
import com.devmind.module.ai.vo.RagEvaluationCaseResponse;
import com.devmind.module.ai.vo.RagEvaluationDatasetResponse;
import com.devmind.module.ai.vo.RagRetrievalEvaluationCaseResponse;
import com.devmind.module.ai.vo.RagRetrievalEvaluationResponse;
import com.devmind.module.ai.vo.RagRetrievalStrategyEvaluationResponse;
import com.devmind.module.search.rerank.RerankClientRouter;
import com.devmind.module.search.strategy.HybridRetrievalStrategy;
import com.devmind.module.search.strategy.KeywordRetrievalStrategy;
import com.devmind.module.search.strategy.RetrievalStrategy;
import com.devmind.module.search.vo.ChunkSearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class RagEvaluationDatasetService {

    private static final Logger log = LoggerFactory.getLogger(RagEvaluationDatasetService.class);

    private static final int RETRIEVAL_EVALUATION_K = 3;
    private static final int RETRIEVAL_EVALUATION_LIMIT = 5;
    private static final String RELEVANCE_MODE = "gold-document-title";
    private static final String LOCAL_SPARSE_PROVIDER = "local-sparse-vector";
    private static final String REMOTE_DENSE_PROVIDER = "remote-dense";
    private static final String REMOTE_RERANK_PROVIDER = "remote-rerank";
    private static final String STATUS_AVAILABLE = "available";
    private static final String STATUS_UNAVAILABLE = "unavailable";

    private final AiAskLogMapper askLogMapper;
    private final HybridRetrievalStrategy hybridRetrievalStrategy;
    private final KeywordRetrievalStrategy keywordRetrievalStrategy;
    private final RetrievalKeywordService retrievalKeywordService;
    private final RerankClientRouter rerankClientRouter;
    private final List<EvaluationCaseDefinition> cases;

    public RagEvaluationDatasetService(AiAskLogMapper askLogMapper,
                                       HybridRetrievalStrategy hybridRetrievalStrategy,
                                       KeywordRetrievalStrategy keywordRetrievalStrategy,
                                       RetrievalKeywordService retrievalKeywordService,
                                       RerankClientRouter rerankClientRouter,
                                       RagEvaluationDatasetCatalog evaluationDatasetCatalog) {
        this.askLogMapper = askLogMapper;
        this.hybridRetrievalStrategy = hybridRetrievalStrategy;
        this.keywordRetrievalStrategy = keywordRetrievalStrategy;
        this.retrievalKeywordService = retrievalKeywordService;
        this.rerankClientRouter = rerankClientRouter;
        this.cases = evaluationDatasetCatalog.cases();
    }

    public RagEvaluationDatasetResponse dataset(Long userId) {
        Map<String, AiAskLog> latestLogByQuestion = loadLatestLogByQuestion(userId);
        List<RagEvaluationCaseResponse> caseResponses = cases.stream()
                .map(caseDefinition -> toResponse(caseDefinition, latestLogByQuestion.get(caseDefinition.question())))
                .toList();

        int coveredCaseCount = (int) caseResponses.stream()
                .filter(response -> Boolean.TRUE.equals(response.getCovered()))
                .count();
        double coverageRate = caseResponses.isEmpty()
                ? 0.0
                : roundToFourDecimals((double) coveredCaseCount / caseResponses.size());

        return new RagEvaluationDatasetResponse(
                caseResponses.size(),
                coveredCaseCount,
                coverageRate,
                caseResponses
        );
    }

    public RagRetrievalEvaluationResponse retrievalEvaluation(Long userId) {
        EvaluationRun baselineRun = evaluateWithStrategy(userId, keywordRetrievalStrategy);
        EvaluationRun sparseRun = evaluateWithHybridProvider(userId, LOCAL_SPARSE_PROVIDER);
        List<RagRetrievalStrategyEvaluationResponse> strategyResults = List.of(
                availableStrategyResult(
                        "keyword-baseline",
                        null,
                        keywordRetrievalStrategy.strategyName(),
                        keywordRetrievalStrategy.description(),
                        baselineRun,
                        baselineRun
                ),
                availableStrategyResult(
                        "sparse-hybrid",
                        LOCAL_SPARSE_PROVIDER,
                        hybridRetrievalStrategy.strategyName(),
                        hybridRetrievalStrategy.description(),
                        sparseRun,
                        baselineRun
                ),
                denseHybridStrategyResult(userId, baselineRun),
                denseHybridPgVectorStrategyResult(userId, baselineRun),
                denseHybridRerankStrategyResult(userId, baselineRun)
        );

        return new RagRetrievalEvaluationResponse(
                sparseRun.caseResponses().size(),
                sparseRun.passedCaseCount(),
                sparseRun.passRate(),
                sparseRun.positiveCaseCount(),
                RETRIEVAL_EVALUATION_K,
                RETRIEVAL_EVALUATION_LIMIT,
                hybridRetrievalStrategy.strategyName(),
                hybridRetrievalStrategy.description(),
                keywordRetrievalStrategy.strategyName(),
                keywordRetrievalStrategy.description(),
                RELEVANCE_MODE,
                sparseRun.hitAtK(),
                sparseRun.mrr(),
                baselineRun.passedCaseCount(),
                baselineRun.passRate(),
                baselineRun.hitAtK(),
                baselineRun.mrr(),
                roundToFourDecimals(sparseRun.hitAtK() - baselineRun.hitAtK()),
                roundToFourDecimals(sparseRun.mrr() - baselineRun.mrr()),
                strategyResults,
                sparseRun.caseResponses()
        );
    }

    private EvaluationRun evaluateWithStrategy(Long userId, RetrievalStrategy strategy) {
        return evaluateWithRetriever(userId, strategy::retrieve);
    }

    private EvaluationRun evaluateWithHybridProvider(Long userId, String provider) {
        return evaluateWithRetriever(userId,
                (caseUserId, keywords, limit) -> hybridRetrievalStrategy.retrieveWithEmbeddingProvider(
                        caseUserId,
                        keywords,
                        limit,
                        provider
                ));
    }

    private EvaluationRun evaluateWithRetriever(Long userId, CaseRetriever retriever) {
        List<RagRetrievalEvaluationCaseResponse> caseResponses = cases.stream()
                .map(caseDefinition -> evaluateRetrievalCase(userId, caseDefinition, retriever))
                .toList();
        return evaluationRun(caseResponses);
    }

    private EvaluationRun evaluateDenseHybridWithRerank(Long userId) {
        List<RagRetrievalEvaluationCaseResponse> caseResponses = cases.stream()
                .map(caseDefinition -> evaluateRetrievalCase(userId, caseDefinition,
                        (caseUserId, keywords, limit) -> {
                            List<ChunkSearchResponse> candidates = hybridRetrievalStrategy.retrieveWithEmbeddingProvider(
                                    caseUserId,
                                    keywords,
                                    limit,
                                    REMOTE_DENSE_PROVIDER
                            );
                            return rerankClientRouter.clientFor(REMOTE_RERANK_PROVIDER)
                                    .rerank(caseDefinition.question(), candidates, limit);
                        }))
                .toList();
        return evaluationRun(caseResponses);
    }

    private EvaluationRun evaluationRun(List<RagRetrievalEvaluationCaseResponse> caseResponses) {
        int passedCaseCount = (int) caseResponses.stream()
                .filter(response -> Boolean.TRUE.equals(response.getPassed()))
                .count();
        double passRate = caseResponses.isEmpty()
                ? 0.0
                : roundToFourDecimals((double) passedCaseCount / caseResponses.size());
        List<RagRetrievalEvaluationCaseResponse> positiveCases = caseResponses.stream()
                .filter(response -> !Boolean.TRUE.equals(response.getExpectedNoContext()))
                .toList();
        int positiveCaseCount = positiveCases.size();
        int hitCount = (int) positiveCases.stream()
                .filter(response -> Boolean.TRUE.equals(response.getHitAtK()))
                .count();
        double hitAtK = positiveCaseCount == 0
                ? 0.0
                : roundToFourDecimals((double) hitCount / positiveCaseCount);
        double mrr = positiveCaseCount == 0
                ? 0.0
                : roundToFourDecimals(positiveCases.stream()
                .map(RagRetrievalEvaluationCaseResponse::getReciprocalRank)
                .mapToDouble(rank -> rank == null ? 0.0 : rank)
                .sum() / positiveCaseCount);

        return new EvaluationRun(caseResponses, passedCaseCount, passRate, positiveCaseCount, hitAtK, mrr);
    }

    private RagRetrievalStrategyEvaluationResponse denseHybridStrategyResult(Long userId, EvaluationRun baselineRun) {
        try {
            EvaluationRun denseRun = evaluateWithHybridProvider(userId, REMOTE_DENSE_PROVIDER);
            return availableStrategyResult(
                    "dense-hybrid",
                    REMOTE_DENSE_PROVIDER,
                    hybridRetrievalStrategy.strategyName(),
                    hybridRetrievalStrategy.description(),
                    denseRun,
                    baselineRun
            );
        } catch (RuntimeException ex) {
            log.warn("Dense hybrid retrieval evaluation is unavailable. provider={}, reason={}",
                    REMOTE_DENSE_PROVIDER,
                    safeUnavailableReason(ex));
            return unavailableStrategyResult(
                    "dense-hybrid",
                    REMOTE_DENSE_PROVIDER,
                    hybridRetrievalStrategy.strategyName(),
                    hybridRetrievalStrategy.description(),
                    safeUnavailableReason(ex)
            );
        }
    }

    /**
     * Same dense embedding, same gold-label cases, but the vector arm is served by the
     * pgvector HNSW index instead of MySQL-JSON brute-force cosine — isolating the
     * effect of the storage/serving layer on retrieval quality.
     */
    private RagRetrievalStrategyEvaluationResponse denseHybridPgVectorStrategyResult(Long userId, EvaluationRun baselineRun) {
        String strategyName = hybridRetrievalStrategy.strategyName() + "+pgvector-hnsw";
        String description = hybridRetrievalStrategy.description() + " with the vector arm served by pgvector HNSW";
        try {
            EvaluationRun pgRun = evaluateWithRetriever(userId,
                    (caseUserId, keywords, limit) -> hybridRetrievalStrategy.retrieveWithEmbeddingProviderAndPgStore(
                            caseUserId,
                            keywords,
                            limit,
                            REMOTE_DENSE_PROVIDER
                    ));
            return availableStrategyResult(
                    "dense-hybrid-pgvector",
                    REMOTE_DENSE_PROVIDER,
                    strategyName,
                    description,
                    pgRun,
                    baselineRun
            );
        } catch (RuntimeException ex) {
            log.warn("Dense hybrid pgvector retrieval evaluation is unavailable. provider={}, reason={}",
                    REMOTE_DENSE_PROVIDER,
                    safeUnavailableReason(ex));
            return unavailableStrategyResult(
                    "dense-hybrid-pgvector",
                    REMOTE_DENSE_PROVIDER,
                    strategyName,
                    description,
                    safeUnavailableReason(ex)
            );
        }
    }

    private RagRetrievalStrategyEvaluationResponse denseHybridRerankStrategyResult(Long userId, EvaluationRun baselineRun) {
        try {
            EvaluationRun denseRerankRun = evaluateDenseHybridWithRerank(userId);
            return availableStrategyResult(
                    "dense-hybrid-rerank",
                    REMOTE_DENSE_PROVIDER,
                    hybridRetrievalStrategy.strategyName() + "+remote-rerank",
                    hybridRetrievalStrategy.description() + " plus remote rerank",
                    denseRerankRun,
                    baselineRun
            );
        } catch (RuntimeException ex) {
            log.warn("Dense hybrid rerank retrieval evaluation is unavailable. embeddingProvider={}, rerankProvider={}, reason={}",
                    REMOTE_DENSE_PROVIDER,
                    REMOTE_RERANK_PROVIDER,
                    safeUnavailableReason(ex));
            return unavailableStrategyResult(
                    "dense-hybrid-rerank",
                    REMOTE_DENSE_PROVIDER,
                    hybridRetrievalStrategy.strategyName() + "+remote-rerank",
                    hybridRetrievalStrategy.description() + " plus remote rerank",
                    safeUnavailableReason(ex)
            );
        }
    }

    private RagRetrievalStrategyEvaluationResponse availableStrategyResult(String strategyKey,
                                                                          String embeddingProvider,
                                                                          String retrievalStrategy,
                                                                          String retrievalStrategyDescription,
                                                                          EvaluationRun run,
                                                                          EvaluationRun baselineRun) {
        return new RagRetrievalStrategyEvaluationResponse(
                strategyKey,
                embeddingProvider,
                retrievalStrategy,
                retrievalStrategyDescription,
                STATUS_AVAILABLE,
                null,
                run.passedCaseCount(),
                run.passRate(),
                run.positiveCaseCount(),
                run.hitAtK(),
                run.mrr(),
                roundToFourDecimals(run.hitAtK() - baselineRun.hitAtK()),
                roundToFourDecimals(run.mrr() - baselineRun.mrr()),
                run.caseResponses()
        );
    }

    private RagRetrievalStrategyEvaluationResponse unavailableStrategyResult(String strategyKey,
                                                                            String embeddingProvider,
                                                                            String retrievalStrategy,
                                                                            String retrievalStrategyDescription,
                                                                            String unavailableReason) {
        return new RagRetrievalStrategyEvaluationResponse(
                strategyKey,
                embeddingProvider,
                retrievalStrategy,
                retrievalStrategyDescription,
                STATUS_UNAVAILABLE,
                unavailableReason,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of()
        );
    }

    private String safeUnavailableReason(RuntimeException ex) {
        return ex.getMessage() == null || ex.getMessage().isBlank()
                ? "dense hybrid retrieval is unavailable"
                : ex.getMessage();
    }

    private Map<String, AiAskLog> loadLatestLogByQuestion(Long userId) {
        List<String> questions = cases.stream()
                .map(EvaluationCaseDefinition::question)
                .toList();
        if (questions.isEmpty()) {
            return Map.of();
        }

        List<AiAskLog> logs = askLogMapper.selectList(new LambdaQueryWrapper<AiAskLog>()
                .eq(AiAskLog::getUserId, userId)
                .in(AiAskLog::getQuestion, questions)
                .orderByDesc(AiAskLog::getCreatedAt)
                .orderByDesc(AiAskLog::getId));

        Map<String, AiAskLog> latestLogByQuestion = new LinkedHashMap<>();
        for (AiAskLog log : logs) {
            latestLogByQuestion.putIfAbsent(log.getQuestion(), log);
        }
        return latestLogByQuestion;
    }

    private RagEvaluationCaseResponse toResponse(EvaluationCaseDefinition caseDefinition, AiAskLog latestLog) {
        boolean covered = latestLog != null;
        return new RagEvaluationCaseResponse(
                caseDefinition.caseId(),
                caseDefinition.category(),
                caseDefinition.question(),
                caseDefinition.expectedKeywords(),
                caseDefinition.expectedAnswer(),
                caseDefinition.expectedEvidence(),
                caseDefinition.riskType(),
                covered,
                covered ? latestLog.getId() : null,
                covered ? latestLog.getStatus() : null,
                covered ? latestLog.getRetrievedChunkCount() : null,
                covered ? latestLog.getRetrievedChunkIds() : null,
                covered ? latestLog.getCreatedAt() : null
        );
    }

    private RagRetrievalEvaluationCaseResponse evaluateRetrievalCase(Long userId,
                                                                    EvaluationCaseDefinition caseDefinition,
                                                                    CaseRetriever retriever) {
        List<String> queryKeywords = retrievalKeywordService.resolveKeywords(caseDefinition.question());
        List<ChunkSearchResponse> chunks = retriever.retrieve(userId, queryKeywords, RETRIEVAL_EVALUATION_LIMIT);
        boolean expectedNoContext = "no_context_negative_case".equals(caseDefinition.riskType());
        List<String> matchedKeywords = matchedExpectedKeywords(caseDefinition.expectedKeywords(), chunks);
        List<String> missingKeywords = missingExpectedKeywords(caseDefinition.expectedKeywords(), matchedKeywords);
        Integer firstRelevantRank = expectedNoContext ? null : firstRelevantRank(caseDefinition.relevantDocumentTitles(), chunks);
        Boolean hitAtK = expectedNoContext
                ? chunks.isEmpty()
                : firstRelevantRank != null && firstRelevantRank <= RETRIEVAL_EVALUATION_K;
        Double reciprocalRank = expectedNoContext
                ? null
                : firstRelevantRank == null ? 0.0 : roundToFourDecimals(1.0 / firstRelevantRank);
        boolean passed = expectedNoContext ? chunks.isEmpty() : Boolean.TRUE.equals(hitAtK);

        return new RagRetrievalEvaluationCaseResponse(
                caseDefinition.caseId(),
                caseDefinition.category(),
                caseDefinition.question(),
                caseDefinition.expectedKeywords(),
                caseDefinition.relevantDocumentTitles(),
                queryKeywords,
                matchedKeywords,
                missingKeywords,
                caseDefinition.expectedEvidence(),
                caseDefinition.riskType(),
                passed,
                expectedNoContext,
                chunks.size(),
                firstRelevantRank,
                hitAtK,
                reciprocalRank,
                topChunkIds(chunks),
                topDocumentTitles(chunks),
                retrievalNote(expectedNoContext, chunks, matchedKeywords, firstRelevantRank, hitAtK)
        );
    }

    private List<String> matchedExpectedKeywords(List<String> expectedKeywords, List<ChunkSearchResponse> chunks) {
        String searchableText = chunks.stream()
                .map(this::searchableText)
                .reduce("", (left, right) -> left + "\n" + right)
                .toLowerCase(Locale.ROOT);

        List<String> matched = new ArrayList<>();
        for (String keyword : expectedKeywords) {
            if (searchableText.contains(keyword.toLowerCase(Locale.ROOT))) {
                matched.add(keyword);
            }
        }
        return matched;
    }

    private Integer firstRelevantRank(List<String> relevantDocumentTitles, List<ChunkSearchResponse> chunks) {
        for (int index = 0; index < chunks.size(); index++) {
            if (isGoldDocumentHit(relevantDocumentTitles, chunks.get(index))) {
                return index + 1;
            }
        }
        return null;
    }

    private boolean isGoldDocumentHit(List<String> relevantDocumentTitles, ChunkSearchResponse chunk) {
        String documentTitle = normalizeTitle(chunk.getDocumentTitle());
        return relevantDocumentTitles.stream()
                .map(this::normalizeTitle)
                .anyMatch(documentTitle::equals);
    }

    private String searchableText(ChunkSearchResponse chunk) {
        return String.join(" ",
                safeText(chunk.getDocumentTitle()),
                safeText(chunk.getSourceType()),
                safeText(chunk.getTags()),
                safeText(chunk.getContent()));
    }

    private List<String> missingExpectedKeywords(List<String> expectedKeywords, List<String> matchedKeywords) {
        Set<String> matchedSet = new LinkedHashSet<>(matchedKeywords);
        return expectedKeywords.stream()
                .filter(keyword -> !matchedSet.contains(keyword))
                .toList();
    }

    private List<Long> topChunkIds(List<ChunkSearchResponse> chunks) {
        return chunks.stream()
                .map(ChunkSearchResponse::getChunkId)
                .toList();
    }

    private List<String> topDocumentTitles(List<ChunkSearchResponse> chunks) {
        return chunks.stream()
                .map(ChunkSearchResponse::getDocumentTitle)
                .filter(title -> title != null && !title.isBlank())
                .distinct()
                .toList();
    }

    private String retrievalNote(boolean expectedNoContext,
                                 List<ChunkSearchResponse> chunks,
                                 List<String> matchedKeywords,
                                 Integer firstRelevantRank,
                                 Boolean hitAtK) {
        if (expectedNoContext && chunks.isEmpty()) {
            return "Expected no-context fallback: no chunks were retrieved.";
        }
        if (expectedNoContext) {
            return "Needs review: this negative case retrieved chunks and may cause unsupported answers.";
        }
        if (chunks.isEmpty()) {
            return "Needs review: no chunks were retrieved for a positive evaluation case.";
        }
        if (Boolean.TRUE.equals(hitAtK)) {
            return "Hit@" + RETRIEVAL_EVALUATION_K + " passed: the first gold document chunk is ranked #" + firstRelevantRank + ".";
        }
        if (firstRelevantRank != null) {
            return "Needs review: a gold document chunk was found at rank #" + firstRelevantRank
                    + ", but it did not enter Top " + RETRIEVAL_EVALUATION_K + ".";
        }
        if (matchedKeywords.isEmpty()) {
            return "Needs review: retrieved chunks exist, but no gold document chunk was retrieved and none of the expected keywords were found.";
        }
        return "Needs review: expected keywords were found, but no gold document chunk was retrieved.";
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private String normalizeTitle(String value) {
        return safeText(value).trim().toLowerCase(Locale.ROOT);
    }

    private double roundToFourDecimals(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private record EvaluationRun(List<RagRetrievalEvaluationCaseResponse> caseResponses,
                                 int passedCaseCount,
                                 double passRate,
                                 int positiveCaseCount,
                                 double hitAtK,
                                 double mrr) {
    }

    @FunctionalInterface
    private interface CaseRetriever {

        List<ChunkSearchResponse> retrieve(Long userId, List<String> keywords, Integer limit);
    }
}
