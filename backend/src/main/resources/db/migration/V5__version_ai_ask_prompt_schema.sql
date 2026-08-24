ALTER TABLE ai_ask_log
    ADD COLUMN prompt_schema_version TINYINT NOT NULL DEFAULT 1
        COMMENT '1 legacy truncated prompt, 2 full prompt with visible citations'
        AFTER prompt_preview;
