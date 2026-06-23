package com.devmind.module.document.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.devmind.common.api.PageResult;
import com.devmind.common.api.ResultCode;
import com.devmind.common.exception.BizException;
import com.devmind.module.document.dto.CreateDocumentRequest;
import com.devmind.module.document.dto.UpdateDocumentRequest;
import com.devmind.module.document.entity.KnowledgeDocument;
import com.devmind.module.document.mapper.KnowledgeDocumentMapper;
import com.devmind.module.document.vo.DocumentChunkResponse;
import com.devmind.module.document.vo.DocumentResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class KnowledgeDocumentService {

    private static final int STATUS_ACTIVE = 1;
    private static final int STATUS_ARCHIVED = 0;
    private static final long MAX_PAGE_SIZE = 50;

    private final KnowledgeDocumentMapper documentMapper;
    private final DocumentChunkService chunkService;

    public KnowledgeDocumentService(KnowledgeDocumentMapper documentMapper,
                                    DocumentChunkService chunkService) {
        this.documentMapper = documentMapper;
        this.chunkService = chunkService;
    }

    @Transactional
    public DocumentResponse create(Long userId, CreateDocumentRequest request) {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setUserId(userId);
        document.setTitle(request.getTitle());
        document.setContent(request.getContent());
        document.setSourceType(request.getSourceType());
        document.setTags(request.getTags());
        document.setSummary(request.getSummary());
        document.setStatus(STATUS_ACTIVE);
        documentMapper.insert(document);
        chunkService.rebuildChunks(userId, document.getId(), request.getContent());
        return toResponse(document);
    }

    public DocumentResponse getDetail(Long userId, Long documentId) {
        return toResponse(findOwnedActiveDocument(userId, documentId));
    }

    public PageResult<DocumentResponse> page(Long userId,
                                             String keyword,
                                             String sourceType,
                                             long pageNo,
                                             long pageSize) {
        long safePageNo = Math.max(pageNo, 1);
        long safePageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);

        LambdaQueryWrapper<KnowledgeDocument> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(KnowledgeDocument::getUserId, userId)
                .eq(KnowledgeDocument::getStatus, STATUS_ACTIVE)
                .eq(StringUtils.hasText(sourceType), KnowledgeDocument::getSourceType, sourceType)
                .and(StringUtils.hasText(keyword), wrapper -> wrapper
                        .like(KnowledgeDocument::getTitle, keyword)
                        .or()
                        .like(KnowledgeDocument::getContent, keyword)
                        .or()
                        .like(KnowledgeDocument::getTags, keyword))
                .orderByDesc(KnowledgeDocument::getUpdatedAt)
                .orderByDesc(KnowledgeDocument::getId);

        Page<KnowledgeDocument> page = documentMapper.selectPage(new Page<>(safePageNo, safePageSize), queryWrapper);
        List<DocumentResponse> records = page.getRecords().stream()
                .map(this::toResponse)
                .toList();

        return new PageResult<>(page.getCurrent(), page.getSize(), page.getTotal(), records);
    }

    public List<DocumentChunkResponse> listChunks(Long userId, Long documentId) {
        findOwnedActiveDocument(userId, documentId);
        return chunkService.listActiveChunks(userId, documentId);
    }

    @Transactional
    public DocumentResponse update(Long userId, Long documentId, UpdateDocumentRequest request) {
        KnowledgeDocument document = findOwnedActiveDocument(userId, documentId);
        document.setTitle(request.getTitle());
        document.setContent(request.getContent());
        document.setSourceType(request.getSourceType());
        document.setTags(request.getTags());
        document.setSummary(request.getSummary());
        documentMapper.updateById(document);
        chunkService.rebuildChunks(userId, documentId, request.getContent());
        return toResponse(document);
    }

    @Transactional
    public void archive(Long userId, Long documentId) {
        KnowledgeDocument document = findOwnedActiveDocument(userId, documentId);
        document.setStatus(STATUS_ARCHIVED);
        documentMapper.updateById(document);
        chunkService.archiveByDocument(userId, documentId);
    }

    private KnowledgeDocument findOwnedActiveDocument(Long userId, Long documentId) {
        LambdaQueryWrapper<KnowledgeDocument> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(KnowledgeDocument::getId, documentId)
                .eq(KnowledgeDocument::getUserId, userId)
                .eq(KnowledgeDocument::getStatus, STATUS_ACTIVE);

        KnowledgeDocument document = documentMapper.selectOne(queryWrapper);
        if (document == null) {
            throw new BizException(ResultCode.NOT_FOUND, "document not found");
        }
        return document;
    }

    private DocumentResponse toResponse(KnowledgeDocument document) {
        return new DocumentResponse(
                document.getId(),
                document.getTitle(),
                document.getContent(),
                document.getSourceType(),
                document.getTags(),
                document.getSummary(),
                document.getStatus(),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }
}
