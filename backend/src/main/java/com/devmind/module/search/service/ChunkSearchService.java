package com.devmind.module.search.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devmind.common.api.ResultCode;
import com.devmind.common.exception.BizException;
import com.devmind.module.document.entity.DocumentChunk;
import com.devmind.module.document.entity.KnowledgeDocument;
import com.devmind.module.document.mapper.DocumentChunkMapper;
import com.devmind.module.document.mapper.KnowledgeDocumentMapper;
import com.devmind.module.search.dto.ChunkFullTextMatch;
import com.devmind.module.search.vo.ChunkSearchResponse;
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

@Service
public class ChunkSearchService {

    private static final int STATUS_ACTIVE = 1;
    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 20;
    // Candidate pool fetched from MySQL before in-memory scoring. Must be much larger
    // than MAX_LIMIT: real relevance ranking happens in calculateScore, so the SQL-side
    // cut only decides which rows are even considered. FULLTEXT candidates are already
    // relevance-ordered; LIKE candidates have no cheap SQL-side relevance order, so they
    // are cut by recency, and this bound is deliberately wide to keep that bias marginal.
    private static final int CANDIDATE_POOL_LIMIT = 200;
    private static final int DOCUMENT_MATCH_LIMIT = 50;

    private final DocumentChunkMapper chunkMapper;
    private final KnowledgeDocumentMapper documentMapper;

    public ChunkSearchService(DocumentChunkMapper chunkMapper,
                              KnowledgeDocumentMapper documentMapper) {
        this.chunkMapper = chunkMapper;
        this.documentMapper = documentMapper;
    }

    public List<ChunkSearchResponse> searchChunks(Long userId, String keyword, Integer limit) {
        if (!StringUtils.hasText(keyword)) {
            throw new BizException(ResultCode.BAD_REQUEST, "keyword is required");
        }

        return searchChunks(userId, List.of(keyword), limit);
    }

    public List<ChunkSearchResponse> searchChunks(Long userId, List<String> keywords, Integer limit) {
        List<String> normalizedKeywords = normalizeKeywords(keywords);
        if (normalizedKeywords.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "keyword is required");
        }

        int safeLimit = resolveLimit(limit);
        Set<Long> matchingDocumentIds = findMatchingDocumentIds(userId, normalizedKeywords);
        List<ChunkFullTextMatch> fullTextMatches = findFullTextMatches(userId, normalizedKeywords, CANDIDATE_POOL_LIMIT);
        Map<Long, Double> fullTextScoreMap = fullTextMatches.stream()
                .filter(match -> match.getId() != null)
                .collect(Collectors.toMap(
                        ChunkFullTextMatch::getId,
                        match -> safeFullTextScore(match.getFullTextScore()),
                        Math::max
                ));

        LambdaQueryWrapper<DocumentChunk> chunkQuery = new LambdaQueryWrapper<>();
        chunkQuery.eq(DocumentChunk::getUserId, userId)
                .eq(DocumentChunk::getStatus, STATUS_ACTIVE)
                .and(wrapper -> {
                    boolean hasCondition = false;
                    for (int i = 0; i < normalizedKeywords.size(); i++) {
                        String keyword = normalizedKeywords.get(i);
                        if (i == 0) {
                            wrapper.like(DocumentChunk::getContent, keyword);
                        } else {
                            wrapper.or().like(DocumentChunk::getContent, keyword);
                        }
                        hasCondition = true;
                    }
                    if (!matchingDocumentIds.isEmpty()) {
                        if (hasCondition) {
                            wrapper.or();
                        }
                        wrapper.in(DocumentChunk::getDocumentId, matchingDocumentIds);
                    }
                })
                .orderByDesc(DocumentChunk::getUpdatedAt)
                .last("LIMIT " + CANDIDATE_POOL_LIMIT);

        List<DocumentChunk> chunks = mergeCandidates(fullTextMatches, chunkMapper.selectList(chunkQuery));
        if (chunks.isEmpty()) {
            return List.of();
        }

        Set<Long> documentIds = chunks.stream()
                .map(DocumentChunk::getDocumentId)
                .collect(Collectors.toSet());
        Map<Long, KnowledgeDocument> documentMap = findActiveDocuments(userId, documentIds);

        List<ChunkSearchResponse> responses = chunks.stream()
                .filter(chunk -> documentMap.containsKey(chunk.getDocumentId()))
                // Lexical-evidence gate: ngram FULLTEXT happily matches shared bigram
                // fragments (e.g. "驱逐策略" hitting a "拒绝策略" note), so a candidate that
                // never contains any extracted keyword literally is treated as noise here.
                // Fuzzy/semantic recall is the vector channel's job, not this one's.
                .filter(chunk -> keywordEvidenceScore(
                        chunk, documentMap.get(chunk.getDocumentId()), normalizedKeywords) > 0)
                .map(chunk -> toResponse(
                        chunk,
                        documentMap.get(chunk.getDocumentId()),
                        normalizedKeywords,
                        fullTextScoreMap.getOrDefault(chunk.getId(), 0.0)
                ))
                .sorted(Comparator.comparing(ChunkSearchResponse::getScore).reversed()
                        .thenComparing(ChunkSearchResponse::getChunkId))
                .toList();

        return applyDuplicateContentPenalty(responses).stream()
                .sorted(Comparator.comparing(ChunkSearchResponse::getScore).reversed()
                        .thenComparing(ChunkSearchResponse::getChunkId))
                .limit(safeLimit)
                .toList();
    }

    private List<ChunkFullTextMatch> findFullTextMatches(Long userId, List<String> keywords, int limit) {
        String fullTextQuery = String.join(" ", keywords);
        if (!StringUtils.hasText(fullTextQuery)) {
            return List.of();
        }
        List<ChunkFullTextMatch> matches = chunkMapper.searchActiveChunksByFullText(userId, fullTextQuery, limit);
        if (matches == null) {
            return List.of();
        }
        return matches;
    }

    private List<DocumentChunk> mergeCandidates(List<ChunkFullTextMatch> fullTextMatches,
                                                List<DocumentChunk> keywordMatches) {
        Map<Long, DocumentChunk> candidates = new HashMap<>();
        List<DocumentChunk> orderedCandidates = new ArrayList<>();

        if (fullTextMatches != null) {
            for (ChunkFullTextMatch match : fullTextMatches) {
                DocumentChunk chunk = toDocumentChunk(match);
                if (chunk.getId() != null && !candidates.containsKey(chunk.getId())) {
                    candidates.put(chunk.getId(), chunk);
                    orderedCandidates.add(chunk);
                }
            }
        }

        if (keywordMatches != null) {
            for (DocumentChunk chunk : keywordMatches) {
                if (chunk.getId() != null && !candidates.containsKey(chunk.getId())) {
                    candidates.put(chunk.getId(), chunk);
                    orderedCandidates.add(chunk);
                }
            }
        }

        return orderedCandidates;
    }

    private DocumentChunk toDocumentChunk(ChunkFullTextMatch match) {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setId(match.getId());
        chunk.setDocumentId(match.getDocumentId());
        chunk.setUserId(match.getUserId());
        chunk.setChunkIndex(match.getChunkIndex());
        chunk.setContent(match.getContent());
        chunk.setTokenCount(match.getTokenCount());
        chunk.setStatus(match.getStatus());
        chunk.setCreatedAt(match.getCreatedAt());
        chunk.setUpdatedAt(match.getUpdatedAt());
        return chunk;
    }

    public List<ChunkSearchResponse> findChunksByIds(Long userId, List<Long> chunkIds) {
        return findChunksByIds(userId, chunkIds, List.of());
    }

    public List<ChunkSearchResponse> findChunksByIds(Long userId, List<Long> chunkIds, List<String> keywords) {
        List<Long> normalizedChunkIds = normalizeChunkIds(chunkIds);
        if (normalizedChunkIds.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "chunk ids are required");
        }
        List<String> normalizedKeywords = normalizeKeywords(keywords);

        LambdaQueryWrapper<DocumentChunk> chunkQuery = new LambdaQueryWrapper<>();
        chunkQuery.eq(DocumentChunk::getUserId, userId)
                .eq(DocumentChunk::getStatus, STATUS_ACTIVE)
                .in(DocumentChunk::getId, normalizedChunkIds);

        List<DocumentChunk> chunks = chunkMapper.selectList(chunkQuery);
        if (chunks.isEmpty()) {
            return List.of();
        }

        Set<Long> documentIds = chunks.stream()
                .map(DocumentChunk::getDocumentId)
                .collect(Collectors.toSet());
        Map<Long, KnowledgeDocument> documentMap = findActiveDocuments(userId, documentIds);
        Map<Long, DocumentChunk> chunkMap = chunks.stream()
                .filter(chunk -> documentMap.containsKey(chunk.getDocumentId()))
                .collect(Collectors.toMap(DocumentChunk::getId, Function.identity()));

        return normalizedChunkIds.stream()
                .map(chunkMap::get)
                .filter(chunk -> chunk != null)
                .map(chunk -> toResponse(chunk, documentMap.get(chunk.getDocumentId()), normalizedKeywords))
                .toList();
    }

    private Set<Long> findMatchingDocumentIds(Long userId, List<String> keywords) {
        LambdaQueryWrapper<KnowledgeDocument> documentQuery = new LambdaQueryWrapper<>();
        documentQuery.eq(KnowledgeDocument::getUserId, userId)
                .eq(KnowledgeDocument::getStatus, STATUS_ACTIVE)
                .and(wrapper -> {
                    for (int i = 0; i < keywords.size(); i++) {
                        String keyword = keywords.get(i);
                        if (i > 0) {
                            wrapper.or();
                        }
                        wrapper.like(KnowledgeDocument::getTitle, keyword)
                                .or()
                                .like(KnowledgeDocument::getTags, keyword)
                                .or()
                                .like(KnowledgeDocument::getSourceType, keyword);
                    }
                })
                .last("LIMIT " + DOCUMENT_MATCH_LIMIT);

        return documentMapper.selectList(documentQuery).stream()
                .map(KnowledgeDocument::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Map<Long, KnowledgeDocument> findActiveDocuments(Long userId, Set<Long> documentIds) {
        LambdaQueryWrapper<KnowledgeDocument> documentQuery = new LambdaQueryWrapper<>();
        documentQuery.eq(KnowledgeDocument::getUserId, userId)
                .eq(KnowledgeDocument::getStatus, STATUS_ACTIVE)
                .in(KnowledgeDocument::getId, documentIds);

        return documentMapper.selectList(documentQuery).stream()
                .collect(Collectors.toMap(KnowledgeDocument::getId, Function.identity()));
    }

    private int resolveLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.min(Math.max(limit, 1), MAX_LIMIT);
    }

    private ChunkSearchResponse toResponse(DocumentChunk chunk,
                                           KnowledgeDocument document,
                                           List<String> keywords) {
        return toResponse(chunk, document, keywords, 0.0);
    }

    private ChunkSearchResponse toResponse(DocumentChunk chunk,
                                           KnowledgeDocument document,
                                           List<String> keywords,
                                           double fullTextScore) {
        return new ChunkSearchResponse(
                chunk.getId(),
                chunk.getDocumentId(),
                document.getTitle(),
                document.getSourceType(),
                document.getTags(),
                chunk.getChunkIndex(),
                chunk.getContent(),
                chunk.getTokenCount(),
                calculateScore(chunk, document, keywords, fullTextScore)
        );
    }

    private List<ChunkSearchResponse> applyDuplicateContentPenalty(List<ChunkSearchResponse> responses) {
        Map<String, Integer> seenContentCounts = new HashMap<>();
        for (ChunkSearchResponse response : responses) {
            String fingerprint = contentFingerprint(response.getContent());
            if (!StringUtils.hasText(fingerprint)) {
                continue;
            }
            int seenCount = seenContentCounts.getOrDefault(fingerprint, 0);
            if (seenCount > 0) {
                response.setScore(Math.max(1, response.getScore() / (seenCount + 1)));
            }
            seenContentCounts.put(fingerprint, seenCount + 1);
        }
        return responses;
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

    private List<Long> normalizeChunkIds(List<Long> chunkIds) {
        if (chunkIds == null) {
            return List.of();
        }

        return chunkIds.stream()
                .filter(chunkId -> chunkId != null && chunkId > 0)
                .limit(MAX_LIMIT)
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(LinkedHashSet::new),
                        List::copyOf
                ));
    }

    private int calculateScore(DocumentChunk chunk,
                               KnowledgeDocument document,
                               List<String> keywords,
                               double fullTextScore) {
        int score = fullTextScoreContribution(fullTextScore) + keywordEvidenceScore(chunk, document, keywords);
        return Math.max(score, 1);
    }

    private int keywordEvidenceScore(DocumentChunk chunk,
                                     KnowledgeDocument document,
                                     List<String> keywords) {
        int score = 0;
        for (String keyword : keywords) {
            String lowerKeyword = keyword.toLowerCase();
            score += countOccurrences(chunk.getContent(), lowerKeyword) * 10;
            score += countOccurrences(document.getTitle(), lowerKeyword) * 5;
            score += countOccurrences(document.getTags(), lowerKeyword) * 3;
            score += countOccurrences(document.getSourceType(), lowerKeyword);
        }
        return score;
    }

    private int fullTextScoreContribution(double fullTextScore) {
        if (fullTextScore <= 0) {
            return 0;
        }
        return Math.min((int) Math.round(fullTextScore * 100), 200);
    }

    private double safeFullTextScore(Double fullTextScore) {
        if (fullTextScore == null || fullTextScore.isNaN() || fullTextScore.isInfinite()) {
            return 0.0;
        }
        return Math.max(fullTextScore, 0.0);
    }

    private int countOccurrences(String text, String lowerKeyword) {
        if (!StringUtils.hasText(text)) {
            return 0;
        }

        String lowerText = text.toLowerCase();
        int count = 0;
        int index = lowerText.indexOf(lowerKeyword);
        while (index >= 0) {
            count++;
            index = lowerText.indexOf(lowerKeyword, index + lowerKeyword.length());
        }
        return count;
    }

    private String contentFingerprint(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        return content.toLowerCase()
                .replaceAll("\\s+", "")
                .replaceAll("[\\p{Punct}，。！？；：“”‘’、（）【】《》]", "");
    }
}
