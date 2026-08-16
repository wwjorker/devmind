package com.devmind.module.ai.service;

import com.devmind.module.search.vo.ChunkSearchResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PromptBuilderService {

    private static final int MAX_CONTEXT_CHARS_PER_CHUNK = 600;
    private static final int MAX_PROMPT_PREVIEW_CHARS = 2000;

    public String buildPrompt(String question, List<ChunkSearchResponse> chunks) {
        return containsChinese(question)
                ? buildChinesePrompt(question, chunks)
                : buildEnglishPrompt(question, chunks);
    }

    public String buildPromptPreview(String prompt) {
        if (prompt == null || prompt.length() <= MAX_PROMPT_PREVIEW_CHARS) {
            return prompt;
        }
        return prompt.substring(0, MAX_PROMPT_PREVIEW_CHARS - 3) + "...";
    }

    private String buildChinesePrompt(String question, List<ChunkSearchResponse> chunks) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是 DevMind，一名面向开发者学习笔记的 AI 助手。\n");
        prompt.append("只能依据提供的检索上下文回答用户问题，不要补充上下文之外的事实。\n");
        prompt.append("如果上下文不足，请明确说明知识库中没有足够信息。\n");
        prompt.append("请使用中文回答，并引用实际使用的知识片段编号。\n\n");

        appendQuestionAndContext(prompt, question, chunks, "问题", "检索上下文", "没有召回相关知识片段。");

        prompt.append("回答格式：\n");
        prompt.append("- 直接回答\n");
        prompt.append("- 关键要点\n");
        prompt.append("- 引用依据：实际使用的 chunkId\n");

        return prompt.toString();
    }

    private String buildEnglishPrompt(String question, List<ChunkSearchResponse> chunks) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are DevMind, an AI assistant for developer learning notes.\n");
        prompt.append("Answer the user's question only with the provided context.\n");
        prompt.append("If the context is insufficient, say that the knowledge base does not contain enough information.\n");
        prompt.append("Answer in the same language as the user's question. If the question is in Chinese, answer in Chinese.\n");
        prompt.append("Cite useful chunks by their chunk ids.\n\n");

        appendQuestionAndContext(prompt, question, chunks, "Question", "Retrieved context", "No relevant chunks were retrieved.");

        prompt.append("Answer format:\n");
        prompt.append("- Direct answer\n");
        prompt.append("- Key points\n");
        prompt.append("- Citations: chunk ids used\n");

        return prompt.toString();
    }

    private void appendQuestionAndContext(StringBuilder prompt,
                                          String question,
                                          List<ChunkSearchResponse> chunks,
                                          String questionHeading,
                                          String contextHeading,
                                          String emptyContextText) {
        prompt.append(questionHeading).append(":\n");
        prompt.append(question).append("\n\n");

        prompt.append(contextHeading).append(":\n");
        if (chunks.isEmpty()) {
            prompt.append("(").append(emptyContextText).append(")\n");
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
    }

    private String limit(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "...";
    }

    private boolean containsChinese(String text) {
        if (text == null) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (Character.UnicodeScript.of(text.charAt(i)) == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }
}
