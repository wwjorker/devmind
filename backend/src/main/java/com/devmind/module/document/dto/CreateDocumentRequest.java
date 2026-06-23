package com.devmind.module.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateDocumentRequest {

    @NotBlank(message = "title is required")
    @Size(max = 120, message = "title must be at most 120 characters")
    private String title;

    @NotBlank(message = "content is required")
    @Size(max = 20000, message = "content must be at most 20000 characters")
    private String content;

    @NotBlank(message = "sourceType is required")
    @Size(max = 32, message = "sourceType must be at most 32 characters")
    private String sourceType;

    @Size(max = 255, message = "tags must be at most 255 characters")
    private String tags;

    @Size(max = 500, message = "summary must be at most 500 characters")
    private String summary;

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
}
