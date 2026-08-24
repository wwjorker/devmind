package com.devmind.module.search.embedding;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.url=jdbc:h2:mem:embedding_transaction_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "devmind.ai.embedding.provider=transaction-probe"
})
@Import(EmbeddingTransactionBoundaryIntegrationTest.ProbeConfiguration.class)
class EmbeddingTransactionBoundaryIntegrationTest {

    private final EmbeddingClientRouter embeddingClientRouter;
    private final TransactionOperations transactionOperations;
    private final TransactionProbeEmbeddingClient probeClient;

    @Autowired
    EmbeddingTransactionBoundaryIntegrationTest(EmbeddingClientRouter embeddingClientRouter,
                                                TransactionOperations transactionOperations,
                                                TransactionProbeEmbeddingClient probeClient) {
        this.embeddingClientRouter = embeddingClientRouter;
        this.transactionOperations = transactionOperations;
        this.probeClient = probeClient;
    }

    @Test
    void routerShouldSuspendAnExistingDatabaseTransactionAroundEmbeddingCall() {
        transactionOperations.executeWithoutResult(status -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            embeddingClientRouter.embed("remote input");
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
        });

        assertThat(probeClient.transactionActiveDuringEmbedding()).isFalse();
    }

    @TestConfiguration
    static class ProbeConfiguration {

        @Bean
        TransactionProbeEmbeddingClient transactionProbeEmbeddingClient() {
            return new TransactionProbeEmbeddingClient();
        }
    }

    static class TransactionProbeEmbeddingClient implements EmbeddingClient {

        private final AtomicReference<Boolean> transactionActiveDuringEmbedding = new AtomicReference<>();

        @Override
        public String providerName() {
            return "transaction-probe";
        }

        @Override
        public Map<String, Double> embed(String text) {
            transactionActiveDuringEmbedding.set(
                    TransactionSynchronizationManager.isActualTransactionActive());
            return Map.of("probe", 1.0);
        }

        @Override
        public double cosineSimilarity(Map<String, Double> left, Map<String, Double> right) {
            return 0.0;
        }

        Boolean transactionActiveDuringEmbedding() {
            return transactionActiveDuringEmbedding.get();
        }
    }
}
