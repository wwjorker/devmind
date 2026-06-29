package com.devmind.module.search.embedding;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Component
public class LocalEmbeddingClient {

    private static final int MIN_CHINESE_GRAM_LENGTH = 2;

    public Map<String, Double> embed(String text) {
        if (!StringUtils.hasText(text)) {
            return Map.of();
        }

        Map<String, Double> vector = new HashMap<>();
        String normalized = text.toLowerCase(Locale.ROOT);
        addAsciiTokens(vector, normalized);
        addChineseBigrams(vector, normalized);
        return normalize(vector);
    }

    public double cosineSimilarity(Map<String, Double> left, Map<String, Double> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return 0.0;
        }

        Map<String, Double> smaller = left.size() <= right.size() ? left : right;
        Map<String, Double> larger = left.size() <= right.size() ? right : left;
        double dotProduct = 0.0;
        for (Map.Entry<String, Double> entry : smaller.entrySet()) {
            dotProduct += entry.getValue() * larger.getOrDefault(entry.getKey(), 0.0);
        }
        return Math.max(dotProduct, 0.0);
    }

    private void addAsciiTokens(Map<String, Double> vector, String text) {
        String[] tokens = text.split("[^a-z0-9]+");
        for (String token : tokens) {
            if (token.length() < 2) {
                continue;
            }
            vector.merge(token, 1.0, Double::sum);
        }
    }

    private void addChineseBigrams(Map<String, Double> vector, String text) {
        StringBuilder chinese = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (Character.UnicodeScript.of(current) == Character.UnicodeScript.HAN) {
                chinese.append(current);
            } else {
                flushChineseBigrams(vector, chinese);
            }
        }
        flushChineseBigrams(vector, chinese);
    }

    private void flushChineseBigrams(Map<String, Double> vector, StringBuilder chinese) {
        if (chinese.length() < MIN_CHINESE_GRAM_LENGTH) {
            chinese.setLength(0);
            return;
        }
        for (int i = 0; i <= chinese.length() - MIN_CHINESE_GRAM_LENGTH; i++) {
            vector.merge(chinese.substring(i, i + MIN_CHINESE_GRAM_LENGTH), 1.0, Double::sum);
        }
        chinese.setLength(0);
    }

    private Map<String, Double> normalize(Map<String, Double> vector) {
        double norm = vector.values().stream()
                .mapToDouble(value -> value * value)
                .sum();
        if (norm <= 0.0) {
            return Map.of();
        }

        double length = Math.sqrt(norm);
        Map<String, Double> normalized = new HashMap<>();
        for (Map.Entry<String, Double> entry : vector.entrySet()) {
            normalized.put(entry.getKey(), entry.getValue() / length);
        }
        return normalized;
    }
}
