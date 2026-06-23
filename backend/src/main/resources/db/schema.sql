CREATE DATABASE IF NOT EXISTS devmind DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE devmind;

CREATE TABLE IF NOT EXISTS user_account (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(64) DEFAULT NULL,
    email VARCHAR(128) DEFAULT NULL,
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1 enabled, 0 disabled',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS knowledge_document (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    title VARCHAR(120) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    source_type VARCHAR(32) NOT NULL COMMENT 'java_note, project_doc, api_doc, db_note, bug_review, interview_review',
    tags VARCHAR(255) DEFAULT NULL,
    summary VARCHAR(500) DEFAULT NULL,
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1 active, 0 archived',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_status_updated (user_id, status, updated_at),
    INDEX idx_user_source_type (user_id, source_type),
    CONSTRAINT fk_knowledge_document_user FOREIGN KEY (user_id) REFERENCES user_account(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS knowledge_document_chunk (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    chunk_index INT NOT NULL,
    content TEXT NOT NULL,
    token_count INT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1 active, 0 archived',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_document_status_index (document_id, status, chunk_index),
    INDEX idx_user_status (user_id, status),
    CONSTRAINT fk_document_chunk_document FOREIGN KEY (document_id) REFERENCES knowledge_document(id),
    CONSTRAINT fk_document_chunk_user FOREIGN KEY (user_id) REFERENCES user_account(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_ask_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    question VARCHAR(500) NOT NULL,
    retrieval_keyword VARCHAR(128) NOT NULL,
    prompt_preview MEDIUMTEXT DEFAULT NULL,
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
