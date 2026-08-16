package com.devmind.module.ai.service;

public final class PromptSchemaVersions {

    // Version 1 logs predate the full-prompt fix. Their citations may include chunks that the model never saw.
    public static final int LEGACY_TRUNCATED_PROMPT = 1;
    public static final int FULL_PROMPT_WITH_VISIBLE_CITATIONS = 2;
    public static final int CURRENT = FULL_PROMPT_WITH_VISIBLE_CITATIONS;

    private PromptSchemaVersions() {
    }

    public static boolean isAnswerGroundingEvaluationEligible(Integer promptSchemaVersion) {
        return promptSchemaVersion != null
                && promptSchemaVersion == FULL_PROMPT_WITH_VISIBLE_CITATIONS;
    }
}
