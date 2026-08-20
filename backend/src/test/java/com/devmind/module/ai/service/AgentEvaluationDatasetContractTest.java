package com.devmind.module.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AgentEvaluationDatasetContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void developmentDatasetShouldContainTwoCasesForEachRootCause() throws Exception {
        assertRootCauseAllocation("v2-development-bad-cases-v1.json", 12, 2);
    }

    @Test
    void sealedDatasetShouldContainFourCasesForEachRootCause() throws Exception {
        assertRootCauseAllocation("v2-sealed-bad-cases-v1.json", 24, 4);
    }

    private void assertRootCauseAllocation(String fileName,
                                           int expectedSize,
                                           int expectedPerCause) throws Exception {
        JsonNode root = read(fileName);
        Map<String, Integer> allocation = new HashMap<>();
        Set<String> ids = new HashSet<>();
        for (JsonNode item : root.path("cases")) {
            assertThat(ids.add(item.path("caseId").asText())).isTrue();
            assertThat(item.path("promptSchemaVersion").asInt()).isEqualTo(2);
            allocation.merge(item.path("goldRootCause").asText(), 1, Integer::sum);
        }

        assertThat(root.path("caseCount").asInt()).isEqualTo(expectedSize);
        assertThat(ids).hasSize(expectedSize);
        assertThat(allocation).hasSize(6);
        assertThat(allocation.values()).allMatch(count -> count == expectedPerCause);
    }

    @Test
    void challengeDatasetShouldBalanceValidCasesAndFourDefectTypes() throws Exception {
        JsonNode root = read("v2-reviewer-challenges-v1.json");
        Map<String, Integer> defects = new HashMap<>();
        int valid = 0;
        for (JsonNode item : root.path("cases")) {
            if (item.path("goldAcceptable").asBoolean()) {
                valid++;
                assertThat(item.path("goldDefectType").isNull()).isTrue();
            } else {
                defects.merge(item.path("goldDefectType").asText(), 1, Integer::sum);
            }
        }

        assertThat(root.path("cases").size()).isEqualTo(24);
        assertThat(valid).isEqualTo(12);
        assertThat(defects).hasSize(4);
        assertThat(defects.values()).allMatch(count -> count == 3);
    }

    @Test
    void runManifestShouldFreezeAllFourArmsAndLeaveResultsEmpty() throws Exception {
        JsonNode manifest = read("v2-run-manifest-template.json");

        java.util.List<String> arms = new java.util.ArrayList<>();
        manifest.path("arms").forEach(node -> arms.add(node.asText()));
        assertThat(arms)
                .containsExactly("rules", "single", "single+self-review", "reviewed-multi");
        assertThat(manifest.path("runStatus").asText()).isEqualTo("template-not-a-result");
        assertThat(manifest.path("provider").isNull()).isTrue();
        assertThat(manifest.path("metrics").path("rootCauseMacroF1").isNull()).isTrue();
    }

    private JsonNode read(String fileName) throws Exception {
        Path path = Path.of("evaluation", fileName);
        assertThat(Files.isRegularFile(path)).isTrue();
        return objectMapper.readTree(Files.readString(path));
    }
}
