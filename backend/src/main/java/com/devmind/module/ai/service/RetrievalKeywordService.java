package com.devmind.module.ai.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RetrievalKeywordService {

    private static final int MAX_KEYWORD_COUNT = 6;
    private static final Pattern ENGLISH_TOKEN_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_+#.-]*");
    private static final Pattern CHINESE_BLOCK_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5]{2,12}");

    private static final Set<String> ENGLISH_STOP_WORDS = Set.of(
            "what", "why", "how", "when", "where", "which", "is", "are", "am",
            "the", "a", "an", "and", "or", "to", "of", "for", "in", "on",
            "with", "about", "explain", "difference", "between", "should", "can",
            "could", "would", "this", "that", "interview"
    );

    private static final List<String> TECH_PHRASES = List.of(
            "缓存穿透",
            "缓存雪崩",
            "缓存击穿",
            "布隆过滤器",
            "空值缓存",
            "分布式锁",
            "接口限流",
            "限流",
            "幂等",
            "事务",
            "索引",
            "数据库",
            "线程池",
            "鉴权",
            "认证",
            "登录",
            "登出",
            "软删除",
            "向量检索",
            "语义检索",
            "混合检索",
            "重排序",
            "调用日志",
            "失败日志",
            "成本统计",
            "知识库",
            "外卖项目",
            "Redis",
            "MySQL",
            "JWT",
            "RAG",
            "Token",
            "Spring",
            "Spring Boot",
            "Docker",
            "Flyway",
            "DeepSeek"
    );

    private final boolean techPhrasesEnabled;

    public RetrievalKeywordService(
            @Value("${devmind.retrieval.tech-phrases-enabled:true}") boolean techPhrasesEnabled) {
        // The curated phrase list is hand-written and can overfit the evaluation corpus.
        // Keeping it switchable lets the evaluation measure retrieval with and without it.
        this.techPhrasesEnabled = techPhrasesEnabled;
    }

    public List<String> resolveKeywords(String question) {
        if (!StringUtils.hasText(question)) {
            return List.of();
        }

        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        if (techPhrasesEnabled) {
            addKnownTechPhrases(question, keywords);
        }
        addEnglishTokens(question, keywords);
        addChineseFallbackTerms(question, keywords);

        if (keywords.isEmpty()) {
            keywords.add(question.trim());
        }

        return keywords.stream()
                .limit(MAX_KEYWORD_COUNT)
                .toList();
    }

    public String toLogKeyword(List<String> keywords) {
        return String.join(",", keywords);
    }

    private void addKnownTechPhrases(String question, LinkedHashSet<String> keywords) {
        String lowerQuestion = question.toLowerCase(Locale.ROOT);
        for (String phrase : TECH_PHRASES) {
            if (lowerQuestion.contains(phrase.toLowerCase(Locale.ROOT))) {
                keywords.add(phrase);
            }
        }
    }

    private void addEnglishTokens(String question, LinkedHashSet<String> keywords) {
        Matcher matcher = ENGLISH_TOKEN_PATTERN.matcher(question);
        while (matcher.find()) {
            String token = normalizeEnglishToken(matcher.group());
            if (token.length() < 2 || ENGLISH_STOP_WORDS.contains(token.toLowerCase(Locale.ROOT))) {
                continue;
            }
            keywords.add(token);
        }
    }

    private void addChineseFallbackTerms(String question, LinkedHashSet<String> keywords) {
        Matcher matcher = CHINESE_BLOCK_PATTERN.matcher(question);
        List<String> blocks = new ArrayList<>();
        while (matcher.find()) {
            blocks.add(matcher.group());
        }

        for (String block : blocks) {
            addMeaningfulChineseSlices(block, keywords);
        }
    }

    private void addMeaningfulChineseSlices(String block, LinkedHashSet<String> keywords) {
        String cleaned = block
                .replace("什么", "")
                .replace("怎么", "")
                .replace("如何", "")
                .replace("为什么", "")
                .replace("区别", "")
                .replace("以及", "")
                .replace("和", "")
                .replace("的", "")
                .replace("是", "")
                .trim();

        if (cleaned.length() >= 2) {
            keywords.add(cleaned);
        }
    }

    private String normalizeEnglishToken(String token) {
        String lowerToken = token.toLowerCase(Locale.ROOT);
        if ("redis".equals(lowerToken)) {
            return "Redis";
        }
        if ("mysql".equals(lowerToken)) {
            return "MySQL";
        }
        if ("jwt".equals(lowerToken)) {
            return "JWT";
        }
        if ("rag".equals(lowerToken)) {
            return "RAG";
        }
        if ("api".equals(lowerToken)) {
            return "API";
        }
        return token;
    }
}
