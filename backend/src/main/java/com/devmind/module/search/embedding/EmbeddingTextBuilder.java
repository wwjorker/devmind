package com.devmind.module.search.embedding;

import com.devmind.module.document.entity.DocumentChunk;
import com.devmind.module.document.entity.KnowledgeDocument;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;

@Component
public class EmbeddingTextBuilder {

    public String buildForChunk(KnowledgeDocument document, DocumentChunk chunk) {
        return joinNonBlank(
                document == null ? null : document.getTitle(),
                document == null ? null : document.getSourceType(),
                document == null ? null : document.getTags(),
                chunk == null ? null : chunk.getContent()
        );
    }

    public String buildForQuery(Collection<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return "";
        }
        return keywords.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(LinkedHashSet::new),
                        values -> String.join(" ", values)
                ));
    }

    private String joinNonBlank(String... values) {
        return java.util.Arrays.stream(values)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.joining(" "));
    }
}
