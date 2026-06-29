package com.devmind.module.search.embedding;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LocalEmbeddingClientTest {

    private final LocalEmbeddingClient embeddingClient = new LocalEmbeddingClient();

    @Test
    void shouldCreateComparableVectorsForRelatedText() {
        assertThat(embeddingClient.providerName()).isEqualTo("local-sparse-vector");

        Map<String, Double> queryVector = embeddingClient.embed("Redis cache penetration");
        Map<String, Double> relatedVector = embeddingClient.embed(
                "Redis cache penetration can repeatedly hit MySQL for missing keys"
        );
        Map<String, Double> unrelatedVector = embeddingClient.embed(
                "Flyway migration manages database schema versions"
        );

        double relatedScore = embeddingClient.cosineSimilarity(queryVector, relatedVector);
        double unrelatedScore = embeddingClient.cosineSimilarity(queryVector, unrelatedVector);

        assertThat(queryVector).isNotEmpty();
        assertThat(relatedScore).isGreaterThan(unrelatedScore);
    }

    @Test
    void shouldReturnZeroSimilarityWhenEitherVectorIsEmpty() {
        assertThat(embeddingClient.cosineSimilarity(Map.of(), embeddingClient.embed("Redis"))).isZero();
        assertThat(embeddingClient.cosineSimilarity(embeddingClient.embed("Redis"), Map.of())).isZero();
    }
}
