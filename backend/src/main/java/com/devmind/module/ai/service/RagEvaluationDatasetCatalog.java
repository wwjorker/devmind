package com.devmind.module.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class RagEvaluationDatasetCatalog {

    static final String RESOURCE_PATH = "evaluation/v1-retrieval-cases.json";
    private static final int SUPPORTED_SCHEMA_VERSION = 1;

    private final EvaluationDatasetDefinition dataset;

    @Autowired
    public RagEvaluationDatasetCatalog(ObjectMapper objectMapper) {
        this(objectMapper, new ClassPathResource(RESOURCE_PATH));
    }

    RagEvaluationDatasetCatalog(ObjectMapper objectMapper, ClassPathResource resource) {
        this.dataset = load(objectMapper, resource);
    }

    public List<EvaluationCaseDefinition> cases() {
        return dataset.cases();
    }

    String datasetId() {
        return dataset.datasetId();
    }

    String datasetVersion() {
        return dataset.datasetVersion();
    }

    private EvaluationDatasetDefinition load(ObjectMapper objectMapper, ClassPathResource resource) {
        try (InputStream inputStream = resource.getInputStream()) {
            EvaluationDatasetDefinition definition = objectMapper.readValue(
                    inputStream,
                    EvaluationDatasetDefinition.class
            );
            validate(definition);
            List<EvaluationCaseDefinition> frozenCases = definition.cases().stream()
                    .map(this::freezeCase)
                    .toList();
            return new EvaluationDatasetDefinition(
                    definition.schemaVersion(),
                    definition.datasetId(),
                    definition.datasetVersion(),
                    definition.frozenAt(),
                    frozenCases
            );
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load RAG evaluation dataset: " + resource.getPath(), ex);
        }
    }

    private void validate(EvaluationDatasetDefinition definition) {
        if (definition == null) {
            throw new IllegalStateException("RAG evaluation dataset must not be null");
        }
        if (definition.schemaVersion() != SUPPORTED_SCHEMA_VERSION) {
            throw new IllegalStateException("Unsupported RAG evaluation dataset schema version: "
                    + definition.schemaVersion());
        }
        requireText(definition.datasetId(), "datasetId");
        requireText(definition.datasetVersion(), "datasetVersion");
        if (definition.frozenAt() == null) {
            throw new IllegalStateException("RAG evaluation dataset frozenAt must not be null");
        }
        if (definition.cases() == null || definition.cases().isEmpty()) {
            throw new IllegalStateException("RAG evaluation dataset must contain cases");
        }

        Set<String> caseIds = new HashSet<>();
        Set<String> questions = new HashSet<>();
        for (EvaluationCaseDefinition evaluationCase : definition.cases()) {
            if (evaluationCase == null) {
                throw new IllegalStateException("RAG evaluation dataset must not contain null cases");
            }
            requireText(evaluationCase.caseId(), "caseId");
            requireText(evaluationCase.category(), "category");
            requireText(evaluationCase.question(), "question");
            requireText(evaluationCase.expectedAnswer(), "expectedAnswer");
            requireText(evaluationCase.expectedEvidence(), "expectedEvidence");
            requireText(evaluationCase.riskType(), "riskType");
            if (evaluationCase.relevantDocumentTitles() == null || evaluationCase.expectedKeywords() == null) {
                throw new IllegalStateException("RAG evaluation case lists must not be null: "
                        + evaluationCase.caseId());
            }
            if (!caseIds.add(evaluationCase.caseId())) {
                throw new IllegalStateException("Duplicate RAG evaluation caseId: " + evaluationCase.caseId());
            }
            if (!questions.add(evaluationCase.question())) {
                throw new IllegalStateException("Duplicate RAG evaluation question: " + evaluationCase.question());
            }
        }
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("RAG evaluation dataset field must not be blank: " + field);
        }
    }

    private EvaluationCaseDefinition freezeCase(EvaluationCaseDefinition evaluationCase) {
        return new EvaluationCaseDefinition(
                evaluationCase.caseId(),
                evaluationCase.category(),
                evaluationCase.question(),
                List.copyOf(evaluationCase.relevantDocumentTitles()),
                List.copyOf(evaluationCase.expectedKeywords()),
                evaluationCase.expectedAnswer(),
                evaluationCase.expectedEvidence(),
                evaluationCase.riskType()
        );
    }

    record EvaluationDatasetDefinition(int schemaVersion,
                                       String datasetId,
                                       String datasetVersion,
                                       LocalDate frozenAt,
                                       List<EvaluationCaseDefinition> cases) {
    }

    public record EvaluationCaseDefinition(String caseId,
                                           String category,
                                           String question,
                                           List<String> relevantDocumentTitles,
                                           List<String> expectedKeywords,
                                           String expectedAnswer,
                                           String expectedEvidence,
                                           String riskType) {
    }
}
