package com.devmind.module.document.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.devmind.module.document.entity.DocumentChunk;
import com.devmind.module.document.mapper.DocumentChunkMapper;
import com.devmind.module.document.vo.DocumentChunkResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class DocumentChunkService {

    private static final int STATUS_ACTIVE = 1;
    private static final int STATUS_ARCHIVED = 0;
    private static final int MAX_CHUNK_CHARS = 900;
    private static final int CHUNK_OVERLAP_CHARS = 120;

    private final DocumentChunkMapper chunkMapper;

    public DocumentChunkService(DocumentChunkMapper chunkMapper) {
        this.chunkMapper = chunkMapper;
    }

    @Transactional
    public void rebuildChunks(Long userId, Long documentId, String content) {
        archiveByDocument(userId, documentId);

        List<String> chunks = splitContent(content);
        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk chunk = new DocumentChunk();
            chunk.setUserId(userId);
            chunk.setDocumentId(documentId);
            chunk.setChunkIndex(i);
            chunk.setContent(chunks.get(i));
            chunk.setTokenCount(estimateTokenCount(chunks.get(i)));
            chunk.setStatus(STATUS_ACTIVE);
            chunkMapper.insert(chunk);
        }
    }

    public List<DocumentChunkResponse> listActiveChunks(Long userId, Long documentId) {
        LambdaQueryWrapper<DocumentChunk> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(DocumentChunk::getUserId, userId)
                .eq(DocumentChunk::getDocumentId, documentId)
                .eq(DocumentChunk::getStatus, STATUS_ACTIVE)
                .orderByAsc(DocumentChunk::getChunkIndex)
                .orderByAsc(DocumentChunk::getId);

        return chunkMapper.selectList(queryWrapper).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void archiveByDocument(Long userId, Long documentId) {
        LambdaUpdateWrapper<DocumentChunk> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(DocumentChunk::getUserId, userId)
                .eq(DocumentChunk::getDocumentId, documentId)
                .eq(DocumentChunk::getStatus, STATUS_ACTIVE)
                .set(DocumentChunk::getStatus, STATUS_ARCHIVED);
        chunkMapper.update(updateWrapper);
    }

    private List<String> splitContent(String content) {
        List<String> chunks = new ArrayList<>();
        if (!StringUtils.hasText(content)) {
            return chunks;
        }

        String normalizedContent = content.replace("\r\n", "\n").replace("\r", "\n").trim();
        String[] paragraphs = normalizedContent.split("\\n\\s*\\n");
        StringBuilder current = new StringBuilder();

        for (String paragraph : paragraphs) {
            String trimmedParagraph = paragraph.trim();
            if (!StringUtils.hasText(trimmedParagraph)) {
                continue;
            }
            if (trimmedParagraph.length() > MAX_CHUNK_CHARS) {
                flushCurrentChunk(chunks, current);
                splitLongParagraph(chunks, trimmedParagraph);
                continue;
            }
            if (current.length() > 0 && current.length() + trimmedParagraph.length() + 2 > MAX_CHUNK_CHARS) {
                flushCurrentChunk(chunks, current);
            }
            if (current.length() > 0) {
                current.append("\n\n");
            }
            current.append(trimmedParagraph);
        }

        flushCurrentChunk(chunks, current);
        return chunks;
    }

    private void splitLongParagraph(List<String> chunks, String paragraph) {
        int start = 0;
        while (start < paragraph.length()) {
            int end = Math.min(start + MAX_CHUNK_CHARS, paragraph.length());
            chunks.add(paragraph.substring(start, end).trim());
            if (end == paragraph.length()) {
                break;
            }
            start = Math.max(end - CHUNK_OVERLAP_CHARS, start + 1);
        }
    }

    private void flushCurrentChunk(List<String> chunks, StringBuilder current) {
        if (current.length() == 0) {
            return;
        }
        chunks.add(current.toString());
        current.setLength(0);
    }

    private int estimateTokenCount(String content) {
        return Math.max(1, (int) Math.ceil(content.length() / 2.0));
    }

    private DocumentChunkResponse toResponse(DocumentChunk chunk) {
        return new DocumentChunkResponse(
                chunk.getId(),
                chunk.getDocumentId(),
                chunk.getChunkIndex(),
                chunk.getContent(),
                chunk.getTokenCount(),
                chunk.getStatus(),
                chunk.getCreatedAt()
        );
    }
}
