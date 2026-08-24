package com.devmind.module.ai.service;

public record RetrievedEvidenceSnapshot(
        Long chunkId,
        Long documentId,
        Integer documentVersionNo,
        String documentTitle,
        String sourceType,
        String tags,
        Integer chunkIndex,
        String modelVisibleContent,
        Integer score
) {
}
