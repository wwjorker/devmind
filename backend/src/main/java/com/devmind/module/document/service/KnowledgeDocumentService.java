package com.devmind.module.document.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.devmind.common.api.PageResult;
import com.devmind.common.api.ResultCode;
import com.devmind.common.exception.BizException;
import com.devmind.module.document.dto.CreateDocumentRequest;
import com.devmind.module.document.dto.UpdateDocumentRequest;
import com.devmind.module.document.entity.DocumentChunk;
import com.devmind.module.document.entity.KnowledgeDocument;
import com.devmind.module.document.mapper.KnowledgeDocumentMapper;
import com.devmind.module.document.vo.DocumentChunkResponse;
import com.devmind.module.document.vo.DocumentResponse;
import com.devmind.module.search.service.ChunkVectorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.List;

@Service
public class KnowledgeDocumentService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeDocumentService.class);
    private static final int STATUS_ACTIVE = 1;
    private static final int STATUS_ARCHIVED = 0;
    private static final long MAX_PAGE_SIZE = 50;
    private static final int MAX_TITLE_LENGTH = 120;
    private static final int MAX_CONTENT_LENGTH = 20000;
    private static final String DEFAULT_IMPORTED_SOURCE_TYPE = "learning_note";

    private final KnowledgeDocumentMapper documentMapper;
    private final DocumentChunkService chunkService;
    private final ChunkVectorService chunkVectorService;
    private final TransactionOperations transactionOperations;

    public KnowledgeDocumentService(KnowledgeDocumentMapper documentMapper,
                                    DocumentChunkService chunkService,
                                    ChunkVectorService chunkVectorService,
                                    TransactionOperations transactionOperations) {
        this.documentMapper = documentMapper;
        this.chunkService = chunkService;
        this.chunkVectorService = chunkVectorService;
        this.transactionOperations = transactionOperations;
    }

    public DocumentResponse create(Long userId, CreateDocumentRequest request) {
        validateCreateRequest(request);
        DocumentWriteResult result = transactionOperations.execute(status -> {
            KnowledgeDocument document = new KnowledgeDocument();
            document.setUserId(userId);
            document.setTitle(request.getTitle());
            document.setContent(request.getContent());
            document.setSourceType(request.getSourceType());
            document.setTags(request.getTags());
            document.setSummary(request.getSummary());
            document.setStatus(STATUS_ACTIVE);
            documentMapper.insert(document);
            List<DocumentChunk> chunks = replaceChunksAndArchiveVectors(
                    userId, document.getId(), request.getContent());
            return new DocumentWriteResult(document, chunks);
        });
        return completeVectorIndexing(userId, result);
    }

    public DocumentResponse importFromFile(Long userId,
                                           MultipartFile file,
                                           String title,
                                           String sourceType,
                                           String tags,
                                           String summary) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "file is required");
        }

        String filename = StringUtils.cleanPath(StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename()
                : "imported-note.txt");
        if (!isSupportedTextFile(filename)) {
            throw new BizException(ResultCode.BAD_REQUEST, "only .txt, .md, and .markdown files are supported");
        }

        String content = readTextContent(file);
        CreateDocumentRequest request = new CreateDocumentRequest();
        request.setTitle(StringUtils.hasText(title) ? title.trim() : defaultTitleFromFilename(filename));
        request.setContent(content);
        request.setSourceType(StringUtils.hasText(sourceType) ? sourceType.trim() : DEFAULT_IMPORTED_SOURCE_TYPE);
        request.setTags(StringUtils.hasText(tags) ? tags.trim() : "");
        request.setSummary(StringUtils.hasText(summary) ? summary.trim() : "导入文件：" + filename);
        return create(userId, request);
    }

    public DocumentResponse getDetail(Long userId, Long documentId) {
        return toResponse(findOwnedActiveDocument(userId, documentId));
    }

    public PageResult<DocumentResponse> page(Long userId,
                                             String keyword,
                                             String sourceType,
                                             long pageNo,
                                             long pageSize) {
        return pageByStatus(userId, keyword, sourceType, STATUS_ACTIVE, pageNo, pageSize);
    }

    public PageResult<DocumentResponse> pageArchived(Long userId,
                                                     long pageNo,
                                                     long pageSize) {
        return pageByStatus(userId, null, null, STATUS_ARCHIVED, pageNo, pageSize);
    }

    private PageResult<DocumentResponse> pageByStatus(Long userId,
                                                      String keyword,
                                                      String sourceType,
                                                      int status,
                                                      long pageNo,
                                                      long pageSize) {
        long safePageNo = Math.max(pageNo, 1);
        long safePageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);

        LambdaQueryWrapper<KnowledgeDocument> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(KnowledgeDocument::getUserId, userId)
                .eq(KnowledgeDocument::getStatus, status)
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

    public DocumentResponse update(Long userId, Long documentId, UpdateDocumentRequest request) {
        DocumentWriteResult result = transactionOperations.execute(status -> {
            KnowledgeDocument document = findOwnedActiveDocument(userId, documentId);
            document.setTitle(request.getTitle());
            document.setContent(request.getContent());
            document.setSourceType(request.getSourceType());
            document.setTags(request.getTags());
            document.setSummary(request.getSummary());
            documentMapper.updateById(document);
            List<DocumentChunk> chunks = replaceChunksAndArchiveVectors(
                    userId, documentId, request.getContent());
            return new DocumentWriteResult(document, chunks);
        });
        return completeVectorIndexing(userId, result);
    }

    public void archive(Long userId, Long documentId) {
        transactionOperations.executeWithoutResult(status -> {
            KnowledgeDocument document = findOwnedActiveDocument(userId, documentId);
            document.setStatus(STATUS_ARCHIVED);
            documentMapper.updateById(document);
            chunkService.archiveByDocument(userId, documentId);
            chunkVectorService.archiveMySqlByDocument(userId, documentId);
        });
        chunkVectorService.archiveServingIndexByDocument(userId, documentId);
    }

    public DocumentResponse restore(Long userId, Long documentId) {
        DocumentWriteResult result = transactionOperations.execute(status -> {
            KnowledgeDocument document = findOwnedDocumentByStatus(userId, documentId, STATUS_ARCHIVED);
            document.setStatus(STATUS_ACTIVE);
            documentMapper.updateById(document);
            List<DocumentChunk> chunks = replaceChunksAndArchiveVectors(
                    userId, documentId, document.getContent());
            return new DocumentWriteResult(document, chunks);
        });
        return completeVectorIndexing(userId, result);
    }

    private List<DocumentChunk> replaceChunksAndArchiveVectors(Long userId,
                                                               Long documentId,
                                                               String content) {
        List<DocumentChunk> chunks = chunkService.replaceChunks(userId, documentId, content);
        chunkVectorService.archiveMySqlByDocument(userId, documentId);
        return chunks;
    }

    private DocumentResponse completeVectorIndexing(Long userId, DocumentWriteResult result) {
        if (result == null) {
            throw new IllegalStateException("document transaction returned no result");
        }
        Long documentId = result.document().getId();
        chunkVectorService.archiveServingIndexByDocument(userId, documentId);
        try {
            chunkVectorService.rebuildVectors(userId, documentId, result.chunks());
        } catch (RuntimeException ex) {
            // Document content and chunks are the primary searchable data. Embeddings are
            // derived: keep keyword/full-text retrieval available and let backfill repair
            // the missing provider vectors instead of rolling back a committed document.
            log.warn("Vector indexing failed after document commit; document remains keyword-searchable "
                            + "and can be repaired by embedding backfill. userId={}, documentId={}",
                    userId, documentId, ex);
        }
        return toResponse(result.document());
    }

    private KnowledgeDocument findOwnedActiveDocument(Long userId, Long documentId) {
        return findOwnedDocumentByStatus(userId, documentId, STATUS_ACTIVE);
    }

    private KnowledgeDocument findOwnedDocumentByStatus(Long userId, Long documentId, int status) {
        LambdaQueryWrapper<KnowledgeDocument> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(KnowledgeDocument::getId, documentId)
                .eq(KnowledgeDocument::getUserId, userId)
                .eq(KnowledgeDocument::getStatus, status);

        KnowledgeDocument document = documentMapper.selectOne(queryWrapper);
        if (document == null) {
            throw new BizException(ResultCode.NOT_FOUND, "document not found");
        }
        return document;
    }

    private void validateCreateRequest(CreateDocumentRequest request) {
        if (request == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "document request is required");
        }
        if (!StringUtils.hasText(request.getTitle())) {
            throw new BizException(ResultCode.BAD_REQUEST, "title is required");
        }
        if (request.getTitle().length() > MAX_TITLE_LENGTH) {
            throw new BizException(ResultCode.BAD_REQUEST, "title must be at most 120 characters");
        }
        if (!StringUtils.hasText(request.getContent())) {
            throw new BizException(ResultCode.BAD_REQUEST, "content is required");
        }
        if (request.getContent().length() > MAX_CONTENT_LENGTH) {
            throw new BizException(ResultCode.BAD_REQUEST, "content must be at most 20000 characters");
        }
        if (!StringUtils.hasText(request.getSourceType())) {
            throw new BizException(ResultCode.BAD_REQUEST, "sourceType is required");
        }
        if (request.getSourceType().length() > 32) {
            throw new BizException(ResultCode.BAD_REQUEST, "sourceType must be at most 32 characters");
        }
        if (request.getTags() != null && request.getTags().length() > 255) {
            throw new BizException(ResultCode.BAD_REQUEST, "tags must be at most 255 characters");
        }
        if (request.getSummary() != null && request.getSummary().length() > 500) {
            throw new BizException(ResultCode.BAD_REQUEST, "summary must be at most 500 characters");
        }
    }

    private String readTextContent(MultipartFile file) {
        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8).trim();
            if (!StringUtils.hasText(content)) {
                throw new BizException(ResultCode.BAD_REQUEST, "file content is empty");
            }
            if (content.length() > MAX_CONTENT_LENGTH) {
                throw new BizException(ResultCode.BAD_REQUEST, "file content must be at most 20000 characters");
            }
            return content;
        } catch (IOException ex) {
            throw new BizException(ResultCode.BAD_REQUEST, "failed to read uploaded file");
        }
    }

    private boolean isSupportedTextFile(String filename) {
        String lowerFilename = filename.toLowerCase(Locale.ROOT);
        return lowerFilename.endsWith(".txt")
                || lowerFilename.endsWith(".md")
                || lowerFilename.endsWith(".markdown");
    }

    private String defaultTitleFromFilename(String filename) {
        String title = filename;
        int slashIndex = Math.max(title.lastIndexOf('/'), title.lastIndexOf('\\'));
        if (slashIndex >= 0) {
            title = title.substring(slashIndex + 1);
        }
        int dotIndex = title.lastIndexOf('.');
        if (dotIndex > 0) {
            title = title.substring(0, dotIndex);
        }
        if (!StringUtils.hasText(title)) {
            title = "导入文档";
        }
        return title.length() <= MAX_TITLE_LENGTH ? title : title.substring(0, MAX_TITLE_LENGTH);
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

    private record DocumentWriteResult(KnowledgeDocument document, List<DocumentChunk> chunks) {
    }
}
