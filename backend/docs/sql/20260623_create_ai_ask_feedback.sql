USE devmind;

CREATE TABLE IF NOT EXISTS ai_ask_feedback (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    ask_log_id BIGINT NOT NULL,
    helpful TINYINT NOT NULL COMMENT '1 helpful, 0 bad case',
    reason VARCHAR(500) DEFAULT NULL,
    expected_answer MEDIUMTEXT DEFAULT NULL,
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1 active, 0 deleted',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_created (user_id, created_at),
    INDEX idx_user_helpful (user_id, helpful, created_at),
    INDEX idx_ask_log (ask_log_id),
    CONSTRAINT fk_ai_ask_feedback_user FOREIGN KEY (user_id) REFERENCES user_account(id),
    CONSTRAINT fk_ai_ask_feedback_log FOREIGN KEY (ask_log_id) REFERENCES ai_ask_log(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
