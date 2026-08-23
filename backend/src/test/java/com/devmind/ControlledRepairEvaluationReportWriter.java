package com.devmind;

import com.devmind.module.ai.entity.RepairProposal;
import com.devmind.module.ai.vo.RagRetrievalEvaluationCaseResponse;
import com.devmind.module.ai.vo.RagRetrievalEvaluationResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

final class ControlledRepairEvaluationReportWriter {

    private ControlledRepairEvaluationReportWriter() {
    }

    static void write(ObjectMapper objectMapper,
                      RepairProposal seededProposal,
                      RepairProposal applied,
                      int appliedDocumentVersion,
                      JsonNode executionResult,
                      RagRetrievalEvaluationResponse before,
                      RagRetrievalEvaluationResponse after,
                      int activeVectorCountBefore,
                      int activeVectorCountAfter) throws Exception {
        Path retrievalDataset = Path.of(
                "src", "main", "resources", "evaluation", "v1-retrieval-cases.json");
        Path demoSeed = Path.of("docs", "sql", "reset-and-seed-demo-data-for-testuser.sql");
        JsonNode datasetMetadata = objectMapper.readTree(Files.readAllBytes(retrievalDataset));
        String knowledgeSnapshot = System.getenv("DEVMIND_EVAL_KNOWLEDGE_SNAPSHOT");
        if (knowledgeSnapshot == null || knowledgeSnapshot.isBlank()) {
            knowledgeSnapshot = "git:working-tree";
        }

        RetrievalMetric beforeSparse = new RetrievalMetric(
                before.getPassedCaseCount(), before.getPassRate(), before.getHitAtK(), before.getMrr());
        RetrievalMetric afterSparse = new RetrievalMetric(
                after.getPassedCaseCount(), after.getPassRate(), after.getHitAtK(), after.getMrr());
        RetrievalMetric beforeKeyword = new RetrievalMetric(
                before.getBaselinePassedCaseCount(), before.getBaselinePassRate(),
                before.getBaselineHitAtK(), before.getBaselineMrr());
        RetrievalMetric afterKeyword = new RetrievalMetric(
                after.getBaselinePassedCaseCount(), after.getBaselinePassRate(),
                after.getBaselineHitAtK(), after.getBaselineMrr());

        ControlledRepairReport report = new ControlledRepairReport(
                1,
                "phase-d-controlled-repair-before-after",
                knowledgeSnapshot,
                "real MySQL controlled repair with deterministic local sparse embeddings; "
                        + "no external model, embedding, rerank, Redis, or pgvector dependency",
                new DatasetIdentity(
                        datasetMetadata.path("datasetId").asText(),
                        datasetMetadata.path("datasetVersion").asText(),
                        sha256(retrievalDataset),
                        sha256(demoSeed)),
                new FairnessControls(
                        "local-sparse-vector",
                        true,
                        true,
                        activeVectorCountBefore,
                        activeVectorCountAfter,
                        before.getTotalCaseCount(),
                        before.getPositiveCaseCount(),
                        before.getEvaluationK(),
                        before.getRetrievalLimit()),
                1.0,
                new TargetRepairEvidence(
                        1,
                        1,
                        seededProposal.getProposalType(),
                        seededProposal.getBaseVersionNo(),
                        appliedDocumentVersion,
                        applied.getStatus(),
                        executionResult.path("regression").path("passed").asBoolean(),
                        "One deterministic seeded metadata-repair fixture; not a generalized success rate."),
                new RetrievalComparison(
                        beforeSparse,
                        afterSparse,
                        delta(beforeSparse, afterSparse),
                        beforeKeyword,
                        afterKeyword,
                        delta(beforeKeyword, afterKeyword),
                        changedCases(before, after)),
                new MetricDelta(
                        round4(after.getHitAtK() - before.getHitAtK()),
                        round4(after.getMrr() - before.getMrr())),
                "The full 40-case retrieval suite is measured before and after the same approved "
                        + "repair against the same MySQL dataset and persisted local vector path.");

        Path output = Path.of("target", "evaluation", "phase-d-controlled-repair-report.json");
        Files.createDirectories(output.getParent());
        Files.writeString(
                output,
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report),
                StandardCharsets.UTF_8);
        assertThat(output).isRegularFile();
    }

    private static List<CaseChange> changedCases(RagRetrievalEvaluationResponse before,
                                                  RagRetrievalEvaluationResponse after) {
        Map<String, RagRetrievalEvaluationCaseResponse> beforeById = new LinkedHashMap<>();
        before.getCases().forEach(item -> beforeById.put(item.getCaseId(), item));
        List<CaseChange> changes = new ArrayList<>();
        for (RagRetrievalEvaluationCaseResponse afterCase : after.getCases()) {
            RagRetrievalEvaluationCaseResponse beforeCase = beforeById.get(afterCase.getCaseId());
            if (!Objects.equals(beforeCase.getFirstRelevantRank(), afterCase.getFirstRelevantRank())
                    || !Objects.equals(beforeCase.getHitAtK(), afterCase.getHitAtK())
                    || !Objects.equals(beforeCase.getTopDocumentTitles(), afterCase.getTopDocumentTitles())) {
                changes.add(new CaseChange(
                        afterCase.getCaseId(),
                        beforeCase.getFirstRelevantRank(),
                        afterCase.getFirstRelevantRank(),
                        beforeCase.getHitAtK(),
                        afterCase.getHitAtK(),
                        beforeCase.getTopDocumentTitles(),
                        afterCase.getTopDocumentTitles()));
            }
        }
        return changes;
    }

    private static MetricDelta delta(RetrievalMetric before, RetrievalMetric after) {
        return new MetricDelta(
                round4(after.hitAtK() - before.hitAtK()),
                round4(after.mrr() - before.mrr()));
    }

    private static double round4(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }

    private static String sha256(Path path) throws Exception {
        byte[] bytes = Files.readAllBytes(path);
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private record ControlledRepairReport(
            int schemaVersion,
            String runType,
            String knowledgeSnapshot,
            String evaluationMode,
            DatasetIdentity datasets,
            FairnessControls fairnessControls,
            double targetRepairRate,
            TargetRepairEvidence targetRepairEvidence,
            RetrievalComparison retrieval,
            MetricDelta legacyRetrievalRegressionDelta,
            String scopeNote) {
    }

    private record DatasetIdentity(
            String retrievalDatasetId,
            String retrievalDatasetVersion,
            String retrievalDatasetSha256,
            String demoSeedSha256) {
    }

    private record FairnessControls(
            String embeddingProvider,
            boolean allActiveChunksBackfilledBeforeBaseline,
            boolean sameRetrievalPathBeforeAndAfter,
            int activeVectorCountBefore,
            int activeVectorCountAfter,
            int totalCaseCount,
            int positiveCaseCount,
            int evaluationK,
            int retrievalLimit) {
    }

    private record TargetRepairEvidence(
            int attemptedCount,
            int passedCount,
            String proposalType,
            int baseDocumentVersion,
            int appliedDocumentVersion,
            String finalProposalStatus,
            boolean targetRegressionPassed,
            String scope) {
    }

    private record RetrievalComparison(
            RetrievalMetric sparseHybridBefore,
            RetrievalMetric sparseHybridAfter,
            MetricDelta sparseHybridDelta,
            RetrievalMetric keywordBaselineBefore,
            RetrievalMetric keywordBaselineAfter,
            MetricDelta keywordBaselineDelta,
            List<CaseChange> changedCases) {
    }

    private record RetrievalMetric(
            int passedCaseCount,
            double passRate,
            double hitAtK,
            double mrr) {
    }

    private record MetricDelta(double hitAtK, double mrr) {
    }

    private record CaseChange(
            String caseId,
            Integer firstRelevantRankBefore,
            Integer firstRelevantRankAfter,
            Boolean hitAtKBefore,
            Boolean hitAtKAfter,
            List<String> topDocumentTitlesBefore,
            List<String> topDocumentTitlesAfter) {
    }
}
