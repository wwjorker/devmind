package com.devmind.module.search.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devmind.common.api.ResultCode;
import com.devmind.common.exception.BizException;
import com.devmind.module.document.entity.DocumentChunk;
import com.devmind.module.document.entity.KnowledgeDocument;
import com.devmind.module.document.mapper.DocumentChunkMapper;
import com.devmind.module.document.mapper.KnowledgeDocumentMapper;
import com.devmind.module.search.vo.ChunkSearchResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
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

        int safeLimit = resolveLimit(limit);
        String normalizedKeyword = keyword.trim();

        LambdaQueryWrapper<DocumentChunk> chunkQuery = new LambdaQueryWrapper<>();
        chunkQuery.eq(DocumentChunk::getUserId, userId)
                .eq(DocumentChunk::getStatus, STATUS_ACTIVE)
                .like(DocumentChunk::getContent, normalizedKeyword)
                .orderByDesc(DocumentChunk::getUpdatedAt)
                .last("LIMIT " + safeLimit);

        List<DocumentChunk> chunks = chunkMapper.selectList(chunkQuery);
        if (chunks.isEmpty()) {
            return List.of();
        }

        Set<Long> documentIds = chunks.stream()
                .map(DocumentChunk::getDocumentId)
                .collect(Collectors.toSet());
        Map<Long, KnowledgeDocument> documentMap = findActiveDocuments(userId, documentIds);

        return chunks.stream()
                .filter(chunk -> documentMap.containsKey(chunk.getDocumentId()))
                .map(chunk -> toResponse(chunk, documentMap.get(chunk.getDocumentId()), normalizedKeyword))
                .sorted(Comparator.comparing(ChunkSearchResponse::getScore).reversed()
                        .thenComparing(ChunkSearchResponse::getChunkId))
                .limit(safeLimit)
                .toList();
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
                                           String keyword) {
        return new ChunkSearchResponse(
                chunk.getId(),
                chunk.getDocumentId(),
                document.getTitle(),
                document.getSourceType(),
                document.getTags(),
                chunk.getChunkIndex(),
                chunk.getContent(),
                chunk.getTokenCount(),
                calculateScore(chunk, document, keyword)
        );
    }

    private int calculateScore(DocumentChunk chunk, KnowledgeDocument document, String keyword) {
        String lowerKeyword = keyword.toLowerCase();
        int score = countOccurrences(chunk.getContent(), lowerKeyword) * 10;
        score += countOccurrences(document.getTitle(), lowerKeyword) * 5;
        score += countOccurrences(document.getTags(), lowerKeyword) * 3;
        score += countOccurrences(document.getSourceType(), lowerKeyword);
        return Math.max(score, 1);
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
}
