package com.devmind.module.document.vo;

import java.time.LocalDateTime;

public class DocumentResponse {

    private Long id;
    private String title;
    private String content;
    private String sourceType;
    private String tags;
    private String summary;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public DocumentResponse() {
    }

    public DocumentResponse(Long id,
                            String title,
                            String content,
                            String sourceType,
                            String tags,
                            String summary,
                            Integer status,
                            LocalDateTime createdAt,
                            LocalDateTime updatedAt) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.sourceType = sourceType;
        this.tags = tags;
        this.summary = summary;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
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

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
