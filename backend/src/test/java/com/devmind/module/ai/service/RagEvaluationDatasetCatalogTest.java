package com.devmind.module.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RagEvaluationDatasetCatalogTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldLoadTheFrozenV1DatasetFromClasspath() {
        RagEvaluationDatasetCatalog catalog = new RagEvaluationDatasetCatalog(objectMapper);

        assertThat(catalog.datasetId()).isEqualTo("devmind-retrieval-v1");
        assertThat(catalog.datasetVersion()).isEqualTo("1.0.0");
        assertThat(catalog.cases()).hasSize(40);
        assertThat(catalog.cases())
                .extracting(RagEvaluationDatasetCatalog.EvaluationCaseDefinition::caseId)
                .doesNotHaveDuplicates()
                .contains(
                        "redis-cache-penetration-basic",
                        "unknown-kubernetes-fallback",
                        "hard-negative-mysql-transaction-vs-index"
                );
        assertThat(catalog.cases())
                .filteredOn(evaluationCase -> "no_context_negative_case".equals(evaluationCase.riskType()))
                .hasSize(5);
    }

    @Test
    void missingDatasetShouldFailFastInsteadOfReturningAnEmptyEvaluation() {
        assertThatThrownBy(() -> new RagEvaluationDatasetCatalog(
                objectMapper,
                new ClassPathResource("evaluation/missing-dataset.json")
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to load RAG evaluation dataset");
    }
}
