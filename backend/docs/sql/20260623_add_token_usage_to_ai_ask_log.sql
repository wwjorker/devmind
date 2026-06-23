USE devmind;

ALTER TABLE ai_ask_log
    ADD COLUMN prompt_tokens INT DEFAULT NULL AFTER mock,
    ADD COLUMN completion_tokens INT DEFAULT NULL AFTER prompt_tokens,
    ADD COLUMN total_tokens INT DEFAULT NULL AFTER completion_tokens;
