ALTER TABLE ai_ask_log
    ADD COLUMN retrieval_snapshot_json MEDIUMTEXT DEFAULT NULL AFTER retrieved_chunk_ids;

CREATE TABLE ai_bad_case (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    source_type VARCHAR(24) NOT NULL,
    source_ref VARCHAR(128) NOT NULL,
    feedback_id BIGINT DEFAULT NULL,
    ask_log_id BIGINT DEFAULT NULL,
    ask_snapshot_json MEDIUMTEXT NOT NULL,
    chunk_snapshot_json MEDIUMTEXT NOT NULL,
    trusted_source_json MEDIUMTEXT DEFAULT NULL,
    prompt_schema_version TINYINT DEFAULT NULL,
    root_cause VARCHAR(64) DEFAULT NULL,
    diagnosis_json MEDIUMTEXT DEFAULT NULL,
    status VARCHAR(32) NOT NULL,
    status_version INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_bad_case_user_source (user_id, source_type, source_ref),
    INDEX idx_bad_case_user_status_updated (user_id, status, updated_at),
    INDEX idx_bad_case_feedback (feedback_id),
    INDEX idx_bad_case_ask_log (ask_log_id),
    CONSTRAINT fk_bad_case_user FOREIGN KEY (user_id) REFERENCES user_account(id),
    CONSTRAINT fk_bad_case_feedback FOREIGN KEY (feedback_id) REFERENCES ai_ask_feedback(id),
    CONSTRAINT fk_bad_case_ask_log FOREIGN KEY (ask_log_id) REFERENCES ai_ask_log(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
