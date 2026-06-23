package com.devmind.module.search.vo;

public class ChunkSearchResponse {

    private Long chunkId;
    private Long documentId;
    private String documentTitle;
    private String sourceType;
    private String tags;
    private Integer chunkIndex;
    private String content;
    private Integer tokenCount;
    private Integer score;

    public ChunkSearchResponse() {
    }

    public ChunkSearchResponse(Long chunkId,
                               Long documentId,
                               String documentTitle,
                               String sourceType,
                               String tags,
                               Integer chunkIndex,
                               String content,
                               Integer tokenCount,
                               Integer score) {
        this.chunkId = chunkId;
        this.documentId = documentId;
        this.documentTitle = documentTitle;
        this.sourceType = sourceType;
        this.tags = tags;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.tokenCount = tokenCount;
        this.score = score;
    }

    public Long getChunkId() {
        return chunkId;
    }

    public void setChunkId(Long chunkId) {
        this.chunkId = chunkId;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public String getDocumentTitle() {
        return documentTitle;
    }

    public void setDocumentTitle(String documentTitle) {
        this.documentTitle = documentTitle;
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

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }
}
