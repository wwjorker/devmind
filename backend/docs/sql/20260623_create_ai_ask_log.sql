USE devmind;

CREATE TABLE IF NOT EXISTS ai_ask_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    question VARCHAR(500) NOT NULL,
    retrieval_keyword VARCHAR(128) NOT NULL,
    answer MEDIUMTEXT NOT NULL,
    model_provider VARCHAR(64) NOT NULL,
    mock TINYINT NOT NULL DEFAULT 1 COMMENT '1 mock answer, 0 real model answer',
    prompt_tokens INT DEFAULT NULL,
    completion_tokens INT DEFAULT NULL,
    total_tokens INT DEFAULT NULL,
    retrieved_chunk_count INT NOT NULL DEFAULT 0,
    retrieved_chunk_ids VARCHAR(500) DEFAULT NULL,
    elapsed_ms BIGINT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1 success, 0 failed',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_created (user_id, created_at),
    INDEX idx_user_keyword (user_id, retrieval_keyword),
    CONSTRAINT fk_ai_ask_log_user FOREIGN KEY (user_id) REFERENCES user_account(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
