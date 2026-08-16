package com.devmind.module.ai.agent;

import com.devmind.common.exception.BizException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TriageDiagnosisCodecTest {

    private final TriageDiagnosisCodec codec = new TriageDiagnosisCodec(new ObjectMapper());

    @Test
    void shouldExposeExactlySixRootCausesAndStrictObjects() {
        JsonNode schema = codec.jsonSchema();

        assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
        assertThat(schema.path("properties").path("rootCause").path("enum")).hasSize(6);
        assertThat(schema.path("properties").path("evidence").path("items")
                .path("additionalProperties").asBoolean()).isFalse();
        assertThat(schema.path("required")).hasSize(5);
    }

    @Test
    void shouldParseEvidenceAndEnforceRootCauseRouting() {
        TriageDiagnosis diagnosis = codec.parse("""
                {
                  "rootCause": "correct_evidence_answer_wrong",
                  "summary": "The cited chunk answers the question, but generation contradicted it.",
                  "evidence": [{
                    "toolCallId": "call-evidence-1",
                    "askLogId": 12,
                    "chunkId": 33,
                    "observation": "Schema v2 log cites chunk 33 and the answer reverses its statement."
                  }],
                  "recommendedRoute": "answer_policy_review",
                  "confidence": 0.91
                }
                """);

        assertThat(diagnosis.rootCause())
                .isEqualTo(TriageRootCause.CORRECT_EVIDENCE_ANSWER_WRONG);
        assertThat(diagnosis.recommendedRoute()).isEqualTo(TriageRoute.ANSWER_POLICY_REVIEW);
        assertThat(diagnosis.evidence()).singleElement()
                .extracting(TriageEvidence::chunkId)
                .isEqualTo(33L);

        assertThatThrownBy(() -> codec.parse("""
                {
                  "rootCause": "correct_evidence_answer_wrong",
                  "summary": "Mismatch",
                  "evidence": [{"toolCallId":"c1","observation":"observed"}],
                  "recommendedRoute": "knowledge_gap_ticket",
                  "confidence": 0.5
                }
                """))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("route does not match");
    }

    @Test
    void shouldRejectUnknownFieldsAndMissingEvidence() {
        assertThatThrownBy(() -> codec.parse("""
                {
                  "rootCause": "knowledge_truly_missing",
                  "summary": "No owned source covers the topic.",
                  "evidence": [],
                  "recommendedRoute": "knowledge_gap_ticket",
                  "confidence": 0.8,
                  "writeNow": true
                }
                """))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("unsupported fields");
    }
}
