package com.devmind.module.search.embedding;

import java.util.Map;

public interface EmbeddingClient {

    String providerName();

    Map<String, Double> embed(String text);

    double cosineSimilarity(Map<String, Double> left, Map<String, Double> right);
}
