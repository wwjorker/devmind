package com.devmind.module.ai.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PromptSchemaVersionsTest {

    @Test
    void onlyFullPromptSchemaShouldBeEligibleForAnswerGroundingEvaluation() {
        assertThat(PromptSchemaVersions.isAnswerGroundingEvaluationEligible(null)).isFalse();
        assertThat(PromptSchemaVersions.isAnswerGroundingEvaluationEligible(
                PromptSchemaVersions.LEGACY_TRUNCATED_PROMPT
        )).isFalse();
        assertThat(PromptSchemaVersions.isAnswerGroundingEvaluationEligible(
                PromptSchemaVersions.FULL_PROMPT_WITH_VISIBLE_CITATIONS
        )).isTrue();
    }
}
