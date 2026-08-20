ALTER TABLE knowledge_document
    ADD COLUMN version_no INT NOT NULL DEFAULT 1 AFTER status;

CREATE TABLE knowledge_document_version (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    title VARCHAR(120) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    tags VARCHAR(255) DEFAULT NULL,
    summary VARCHAR(500) DEFAULT NULL,
    document_status TINYINT NOT NULL,
    origin VARCHAR(32) NOT NULL,
    source_evidence_json MEDIUMTEXT DEFAULT NULL,
    proposal_id BIGINT DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_document_version (document_id, version_no),
    INDEX idx_document_version_user_document (user_id, document_id, version_no),
    INDEX idx_document_version_proposal (proposal_id),
    CONSTRAINT fk_document_version_document FOREIGN KEY (document_id)
        REFERENCES knowledge_document(id),
    CONSTRAINT fk_document_version_user FOREIGN KEY (user_id)
        REFERENCES user_account(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO knowledge_document_version (
    document_id,
    user_id,
    version_no,
    title,
    content,
    source_type,
    tags,
    summary,
    document_status,
    origin,
    created_at
)
SELECT
    id,
    user_id,
    version_no,
    title,
    content,
    source_type,
    tags,
    summary,
    status,
    'MIGRATION_BASELINE',
    updated_at
FROM knowledge_document;
