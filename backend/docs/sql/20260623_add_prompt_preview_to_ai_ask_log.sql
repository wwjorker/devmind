ALTER TABLE devmind.ai_ask_log
    ADD COLUMN prompt_preview MEDIUMTEXT DEFAULT NULL AFTER retrieval_keyword;
