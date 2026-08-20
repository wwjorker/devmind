package com.devmind.module.ai.service;

import com.devmind.module.document.entity.DocumentChunk;

import java.util.List;

public record MetadataMutationResult(
        Long documentId,
        int versionNo,
        List<DocumentChunk> activeChunks
) {
    public MetadataMutationResult {
        activeChunks = activeChunks == null ? List.of() : List.copyOf(activeChunks);
    }
}
