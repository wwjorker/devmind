ALTER TABLE agent_step
    ADD COLUMN tool_call_id VARCHAR(128) DEFAULT NULL AFTER tool_name;

CREATE INDEX idx_agent_step_run_tool_call
    ON agent_step (run_id, tool_call_id);
