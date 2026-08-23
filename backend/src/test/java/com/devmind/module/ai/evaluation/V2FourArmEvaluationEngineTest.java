package com.devmind.module.ai.evaluation;

import com.devmind.module.ai.agent.AgentMessage;
import com.devmind.module.ai.agent.AgentModelClient;
import com.devmind.module.ai.agent.AgentModelRequest;
import com.devmind.module.ai.agent.AgentModelResponse;
import com.devmind.module.ai.agent.AgentTokenUsage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class V2FourArmEvaluationEngineTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void runsFrozenArmsWithoutSendingGoldFieldsToTheModel() {
        ObjectNode sealed = mapper.createObjectNode();
        sealed.putArray("cases").addObject()
                .put("caseId", "sealed-test-1")
                .put("issue", "No document covers the requested failover procedure.")
                .put("promptSchemaVersion", 2)
                .put("goldRootCause", "KNOWLEDGE_MISSING");
        ObjectNode challenges = mapper.createObjectNode();
        challenges.putArray("cases").addObject()
                .put("caseId", "review-test-1")
                .put("proposal", "Add a verified alias supported by the current document version.")
                .put("goldAcceptable", true)
                .putNull("goldDefectType");
        challenges.withArray("cases").addObject()
                .put("caseId", "review-test-2")
                .put("proposal", "Claim idempotency although no cited source supports it.")
                .put("goldAcceptable", false)
                .put("goldDefectType", "UNSUPPORTED_CLAIM");
        RecordingClient client = new RecordingClient();
        V2FourArmEvaluationEngine engine = new V2FourArmEvaluationEngine(
                mapper,
                Clock.fixed(Instant.parse("2026-08-20T12:00:00Z"), ZoneOffset.UTC));

        ObjectNode report = engine.run(
                sealed,
                challenges,
                client,
                new V2FourArmEvaluationEngine.RunMetadata(
                        "deepseek",
                        "frozen-test-model",
                        "deepseek:frozen-test-model",
                        "https://api.example/v1",
                        "git:test",
                        "sealed-hash",
                        "challenge-hash",
                        "legacy-hash",
                        new BigDecimal("1.00"),
                        new BigDecimal("2.00"),
                        "test list price"));

        assertThat(report.path("arms").fieldNames())
                .toIterable()
                .containsExactlyElementsOf(V2FourArmEvaluationEngine.ARMS);
        assertThat(report.path("evaluationMode").asText())
                .contains("prompt-only", "tool loop not invoked");
        assertThat(report.path("claimBoundary").asText())
                .contains("resume claim is withheld", "alone cannot authorize");
        assertThat(report.path("protocolVersion").asText())
                .isEqualTo("1.0.0+amendment-2026-08-23");
        assertThat(report.path("providerEndpoint").asText()).isEqualTo("https://api.example/v1");
        assertThat(report.path("pricingBasis").asText()).isEqualTo("test list price");
        assertThat(report.path("arms").path("rules").path("rootCause").path("accuracy").asDouble())
                .isEqualTo(1.0);
        assertThat(report.path("arms").path("reviewed-multi")
                .path("reviewerChallenges").path("validProposalAcceptRate").asDouble())
                .isEqualTo(1.0);
        assertThat(report.path("arms").path("reviewed-multi")
                .path("reviewerChallenges").path("defectCaptureRate").asDouble())
                .isEqualTo(1.0);
        assertThat(report.path("arms").path("reviewed-multi")
                .path("reviewerChallenges").path("confusionMatrix").path("defectiveRejected").asInt())
                .isEqualTo(1);
        assertThat(report.path("arms").path("reviewed-multi")
                .path("reviewerChallenges").path("degeneratePrediction").asBoolean())
                .isFalse();
        assertThat(report.path("runStatus").asText()).isEqualTo("scored-provider-run");
        assertThat(report.path("runValidity").path("degenerateModelArm").asBoolean()).isFalse();
        assertThat(report.path("claimGate").path("resumeClaimAllowed").asBoolean()).isFalse();
        assertThat(report.path("claimGate").path("endToEndQualitySatisfied").isNull()).isTrue();
        assertThat(client.requests).hasSize(15);
        assertThat(client.requests).allSatisfy(request -> {
            String userJson = request.messages().get(1).content();
            assertThat(userJson).doesNotContain("goldRootCause", "goldAcceptable", "goldDefectType");
            assertThat(request.tools()).isEmpty();
        });
        assertThat(client.requests).anySatisfy(request -> {
            assertThat(request.messages().get(0).content())
                    .contains("Independently judge the original proposal", "Agreement is allowed")
                    .doesNotContain("candidate is untrusted and not authoritative");
            assertThat(request.messages().get(1).content())
                    .contains("review-test-2", "candidateJson");
        });
    }

    @Test
    void recordsTheRulesCeilingOnTheFrozenDatasets() throws Exception {
        ObjectNode report = new V2FourArmEvaluationEngine(mapper).run(
                mapper.readTree(Files.readString(Path.of(
                        "evaluation", "v2-sealed-bad-cases-v1.json"))),
                mapper.readTree(Files.readString(Path.of(
                        "evaluation", "v2-reviewer-challenges-v1.json"))),
                new FailingClient(),
                new V2FourArmEvaluationEngine.RunMetadata(
                        "unavailable", "unavailable", "unavailable:model",
                        "offline", "rules-contract-test", "sealed", "challenges", "legacy",
                        null, null, "not applicable"));

        ObjectNode rules = (ObjectNode) report.path("arms").path("rules");
        assertThat(rules.path("rootCause").path("correctCount").asInt()).isEqualTo(24);
        assertThat(rules.path("reviewerChallenges").path("defectCaptureCount").asInt()).isEqualTo(12);
        assertThat(rules.path("reviewerChallenges").path("validProposalAcceptCount").asInt())
                .isEqualTo(12);
    }

    @Test
    void invalidatesAProviderRunWhenAnyModelArmCollapsesToOneVerdict() {
        ObjectNode sealed = mapper.createObjectNode();
        sealed.putArray("cases").addObject()
                .put("caseId", "sealed-degenerate-1")
                .put("issue", "No owned source documents the procedure.")
                .put("promptSchemaVersion", 2)
                .put("goldRootCause", "KNOWLEDGE_MISSING");
        ObjectNode challenges = mapper.createObjectNode();
        challenges.putArray("cases").addObject()
                .put("caseId", "review-valid")
                .put("proposal", "Add a verified current-version alias.")
                .put("goldAcceptable", true)
                .putNull("goldDefectType");
        challenges.withArray("cases").addObject()
                .put("caseId", "review-defective")
                .put("proposal", "Claim encryption without evidence.")
                .put("goldAcceptable", false)
                .put("goldDefectType", "UNSUPPORTED_CLAIM");

        ObjectNode report = new V2FourArmEvaluationEngine(mapper).run(
                sealed,
                challenges,
                new AlwaysAcceptClient(),
                new V2FourArmEvaluationEngine.RunMetadata(
                        "deepseek", "test", "deepseek:test", "https://api.example/v1",
                        "git:test", "sealed", "challenges", "legacy",
                        BigDecimal.ONE, BigDecimal.ONE, "test price"));

        assertThat(report.path("runStatus").asText()).isEqualTo("invalid-degenerate-arm");
        assertThat(report.path("runValidity").path("degenerateModelArm").asBoolean()).isTrue();
        assertThat(report.path("arms").path("reviewed-multi")
                .path("reviewerChallenges").path("confusionMatrix").path("defectiveAccepted").asInt())
                .isEqualTo(1);
    }

    private static final class RecordingClient implements AgentModelClient {
        private final List<AgentModelRequest> requests = new ArrayList<>();

        @Override
        public boolean supports(String provider) {
            return "deepseek".equals(provider);
        }

        @Override
        public AgentModelResponse complete(AgentModelRequest request) {
            requests.add(request);
            String system = request.messages().get(0).content();
            String user = request.messages().get(1).content();
            String content = system.contains("repair proposal")
                    ? user.contains("no cited source")
                    ? "{\"acceptable\":false,\"defectType\":\"UNSUPPORTED_CLAIM\"}"
                    : "{\"acceptable\":true,\"defectType\":null}"
                    : "{\"rootCause\":\"KNOWLEDGE_MISSING\"}";
            return new AgentModelResponse(
                    AgentMessage.assistant(content),
                    "stop",
                    "deepseek:frozen-test-model",
                    new AgentTokenUsage(20, 10, 30));
        }
    }

    private static final class FailingClient implements AgentModelClient {
        @Override
        public boolean supports(String provider) {
            return false;
        }

        @Override
        public AgentModelResponse complete(AgentModelRequest request) {
            throw new IllegalStateException("model intentionally unavailable");
        }
    }

    private static final class AlwaysAcceptClient implements AgentModelClient {
        @Override
        public boolean supports(String provider) {
            return "deepseek".equals(provider);
        }

        @Override
        public AgentModelResponse complete(AgentModelRequest request) {
            String system = request.messages().get(0).content();
            String content = system.contains("repair proposal")
                    ? "{\"acceptable\":true,\"defectType\":null}"
                    : "{\"rootCause\":\"KNOWLEDGE_MISSING\"}";
            return new AgentModelResponse(
                    AgentMessage.assistant(content), "stop", "deepseek:test",
                    new AgentTokenUsage(20, 10, 30));
        }
    }
}
