CREATE TABLE IF NOT EXISTS knowledge_document_chunk_vector (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    chunk_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    provider_name VARCHAR(64) NOT NULL,
    vector_json MEDIUMTEXT NOT NULL,
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1 active, 0 archived',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_chunk_provider (chunk_id, provider_name),
    INDEX idx_user_provider_status (user_id, provider_name, status),
    INDEX idx_document_status (document_id, status),
    CONSTRAINT fk_chunk_vector_chunk FOREIGN KEY (chunk_id) REFERENCES knowledge_document_chunk(id) ON DELETE CASCADE,
    CONSTRAINT fk_chunk_vector_document FOREIGN KEY (document_id) REFERENCES knowledge_document(id),
    CONSTRAINT fk_chunk_vector_user FOREIGN KEY (user_id) REFERENCES user_account(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
