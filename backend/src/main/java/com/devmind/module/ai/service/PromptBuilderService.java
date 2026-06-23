package com.devmind.module.ai.service;

import com.devmind.module.search.vo.ChunkSearchResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PromptBuilderService {

    private static final int MAX_CONTEXT_CHARS_PER_CHUNK = 600;
    private static final int MAX_PROMPT_PREVIEW_CHARS = 2000;

    public String buildPrompt(String question, List<ChunkSearchResponse> chunks) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are DevMind, an AI assistant for developer learning notes.\n");
        prompt.append("Answer the user's question only with the provided context.\n");
        prompt.append("If the context is insufficient, say that the knowledge base does not contain enough information.\n");
        prompt.append("Cite useful chunks by their chunk ids.\n\n");

        prompt.append("Question:\n");
        prompt.append(question).append("\n\n");

        prompt.append("Retrieved context:\n");
        if (chunks.isEmpty()) {
            prompt.append("(No relevant chunks were retrieved.)\n");
        } else {
            for (ChunkSearchResponse chunk : chunks) {
                prompt.append("[chunkId=")
                        .append(chunk.getChunkId())
                        .append(", documentId=")
                        .append(chunk.getDocumentId())
                        .append(", title=")
                        .append(chunk.getDocumentTitle())
                        .append(", score=")
                        .append(chunk.getScore())
                        .append("]\n");
                prompt.append(limit(chunk.getContent(), MAX_CONTEXT_CHARS_PER_CHUNK)).append("\n\n");
            }
        }

        prompt.append("Answer format:\n");
        prompt.append("- Direct answer\n");
        prompt.append("- Key points\n");
        prompt.append("- Citations: chunk ids used\n");

        return limit(prompt.toString(), MAX_PROMPT_PREVIEW_CHARS);
    }

    private String limit(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "...";
    }
}
