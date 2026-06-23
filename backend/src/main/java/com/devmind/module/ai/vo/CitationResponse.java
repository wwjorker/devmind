package com.devmind.module.ai.vo;

public class CitationResponse {

    private Long chunkId;
    private Long documentId;
    private String documentTitle;
    private Integer chunkIndex;
    private Integer score;

    public CitationResponse() {
    }

    public CitationResponse(Long chunkId,
                            Long documentId,
                            String documentTitle,
                            Integer chunkIndex,
                            Integer score) {
        this.chunkId = chunkId;
        this.documentId = documentId;
        this.documentTitle = documentTitle;
        this.chunkIndex = chunkIndex;
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

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(Integer chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }
}
