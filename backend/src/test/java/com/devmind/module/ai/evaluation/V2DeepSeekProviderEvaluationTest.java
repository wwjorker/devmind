package com.devmind.module.ai.evaluation;

import com.devmind.module.ai.agent.DeepSeekAgentModelClient;
import com.devmind.module.ai.config.AiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "DEVMIND_RUN_V2_EVALUATION", matches = "(?i)true")
@EnabledIfEnvironmentVariable(named = "DEVMIND_DEEPSEEK_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DEVMIND_DEEPSEEK_MODEL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DEVMIND_EVAL_KNOWLEDGE_SNAPSHOT", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DEVMIND_DEEPSEEK_INPUT_USD_PER_MILLION", matches = "[0-9]+(\\.[0-9]+)?")
@EnabledIfEnvironmentVariable(named = "DEVMIND_DEEPSEEK_OUTPUT_USD_PER_MILLION", matches = "[0-9]+(\\.[0-9]+)?")
class V2DeepSeekProviderEvaluationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void writesARealFrozenFourArmReportToTarget() throws Exception {
        Path sealedPath = Path.of("evaluation", "v2-sealed-bad-cases-v1.json");
        Path challengePath = Path.of("evaluation", "v2-reviewer-challenges-v1.json");
        Path legacyPath = Path.of("src", "main", "resources", "evaluation", "v1-retrieval-cases.json");
        byte[] sealedBytes = Files.readAllBytes(sealedPath);
        byte[] challengeBytes = Files.readAllBytes(challengePath);
        byte[] legacyBytes = Files.readAllBytes(legacyPath);

        AiProperties properties = new AiProperties();
        properties.setDeepseekApiKey(System.getenv("DEVMIND_DEEPSEEK_API_KEY"));
        properties.setDeepseekModel(System.getenv("DEVMIND_DEEPSEEK_MODEL"));
        properties.setDeepseekTemperature(0.0);
        setIfPresent(System.getenv("DEVMIND_DEEPSEEK_BASE_URL"), properties::setDeepseekBaseUrl);
        DeepSeekAgentModelClient client = new DeepSeekAgentModelClient(properties);
        V2FourArmEvaluationEngine engine = new V2FourArmEvaluationEngine(mapper);
        JsonNode sealed = mapper.readTree(sealedBytes);
        JsonNode challenges = mapper.readTree(challengeBytes);

        ObjectNode report = engine.run(
                sealed,
                challenges,
                client,
                new V2FourArmEvaluationEngine.RunMetadata(
                        "deepseek",
                        properties.getDeepseekModel(),
                        "deepseek:" + properties.getDeepseekModel(),
                        System.getenv("DEVMIND_EVAL_KNOWLEDGE_SNAPSHOT"),
                        sha256(sealedBytes),
                        sha256(challengeBytes),
                        sha256(legacyBytes),
                        new BigDecimal(System.getenv("DEVMIND_DEEPSEEK_INPUT_USD_PER_MILLION")),
                        new BigDecimal(System.getenv("DEVMIND_DEEPSEEK_OUTPUT_USD_PER_MILLION"))));

        Path output = Path.of("target", "evaluation", "v2-four-arm-provider-report.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report),
                StandardCharsets.UTF_8);

        assertThat(report.path("arms").size()).isEqualTo(4);
        assertThat(report.toString()).doesNotContain(System.getenv("DEVMIND_DEEPSEEK_API_KEY"));
        assertThat(output).isRegularFile();
    }

    private String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private void setIfPresent(String value, java.util.function.Consumer<String> setter) {
        if (value != null && !value.isBlank()) setter.accept(value);
    }
}
