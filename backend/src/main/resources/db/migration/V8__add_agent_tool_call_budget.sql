ALTER TABLE agent_run
    ADD COLUMN max_tool_calls INT NOT NULL DEFAULT 20 AFTER max_model_calls;

ALTER TABLE agent_run
    ADD COLUMN used_tool_calls INT NOT NULL DEFAULT 0 AFTER used_model_calls;

UPDATE agent_run
SET max_tool_calls = max_steps;
