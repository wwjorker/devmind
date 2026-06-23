package com.devmind.module.document.vo;

import java.time.LocalDateTime;

public class DocumentChunkResponse {

    private Long id;
    private Long documentId;
    private Integer chunkIndex;
    private String content;
    private Integer tokenCount;
    private Integer status;
    private LocalDateTime createdAt;

    public DocumentChunkResponse() {
    }

    public DocumentChunkResponse(Long id,
                                 Long documentId,
                                 Integer chunkIndex,
                                 String content,
                                 Integer tokenCount,
                                 Integer status,
                                 LocalDateTime createdAt) {
        this.id = id;
        this.documentId = documentId;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.tokenCount = tokenCount;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(Integer chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getTokenCount() {
        return tokenCount;
    }

    public void setTokenCount(Integer tokenCount) {
        this.tokenCount = tokenCount;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
