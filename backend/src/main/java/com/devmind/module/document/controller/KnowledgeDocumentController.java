package com.devmind.module.document.controller;

import com.devmind.common.api.PageResult;
import com.devmind.common.api.Result;
import com.devmind.common.security.AuthenticatedUser;
import com.devmind.module.document.dto.CreateDocumentRequest;
import com.devmind.module.document.dto.UpdateDocumentRequest;
import com.devmind.module.document.service.KnowledgeDocumentService;
import com.devmind.module.document.vo.DocumentChunkResponse;
import com.devmind.module.document.vo.DocumentResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/documents")
public class KnowledgeDocumentController {

    private final KnowledgeDocumentService documentService;

    public KnowledgeDocumentController(KnowledgeDocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping
    public Result<DocumentResponse> create(@AuthenticationPrincipal AuthenticatedUser user,
                                           @Valid @RequestBody CreateDocumentRequest request) {
        return Result.success(documentService.create(user.userId(), request));
    }

    @GetMapping("/{documentId}")
    public Result<DocumentResponse> getDetail(@AuthenticationPrincipal AuthenticatedUser user,
                                              @PathVariable Long documentId) {
        return Result.success(documentService.getDetail(user.userId(), documentId));
    }

    @GetMapping
    public Result<PageResult<DocumentResponse>> page(@AuthenticationPrincipal AuthenticatedUser user,
                                                     @RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) String sourceType,
                                                     @RequestParam(defaultValue = "1") long pageNo,
                                                     @RequestParam(defaultValue = "10") long pageSize) {
        return Result.success(documentService.page(user.userId(), keyword, sourceType, pageNo, pageSize));
    }

    @GetMapping("/{documentId}/chunks")
    public Result<List<DocumentChunkResponse>> listChunks(@AuthenticationPrincipal AuthenticatedUser user,
                                                         @PathVariable Long documentId) {
        return Result.success(documentService.listChunks(user.userId(), documentId));
    }

    @PutMapping("/{documentId}")
    public Result<DocumentResponse> update(@AuthenticationPrincipal AuthenticatedUser user,
                                           @PathVariable Long documentId,
                                           @Valid @RequestBody UpdateDocumentRequest request) {
        return Result.success(documentService.update(user.userId(), documentId, request));
    }

    @DeleteMapping("/{documentId}")
    public Result<Void> archive(@AuthenticationPrincipal AuthenticatedUser user,
                                @PathVariable Long documentId) {
        documentService.archive(user.userId(), documentId);
        return Result.success();
    }
}
