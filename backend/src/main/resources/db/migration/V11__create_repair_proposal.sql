CREATE TABLE repair_proposal (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    bad_case_id BIGINT NOT NULL,
    proposal_type VARCHAR(32) NOT NULL,
    target_document_id BIGINT DEFAULT NULL,
    base_version_no INT DEFAULT NULL,
    diff_json MEDIUMTEXT NOT NULL,
    evidence_json MEDIUMTEXT NOT NULL,
    counterevidence_json MEDIUMTEXT NOT NULL,
    impact_json MEDIUMTEXT NOT NULL,
    regression_plan_json MEDIUMTEXT NOT NULL,
    reviewer_verdict VARCHAR(16) DEFAULT NULL,
    reviewer_findings_json MEDIUMTEXT DEFAULT NULL,
    revision_no INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    approval_idempotency_key VARCHAR(128) DEFAULT NULL,
    approved_diff_json MEDIUMTEXT DEFAULT NULL,
    decision_comment VARCHAR(500) DEFAULT NULL,
    execution_idempotency_key VARCHAR(128) DEFAULT NULL,
    execution_result_json MEDIUMTEXT DEFAULT NULL,
    error_code VARCHAR(64) DEFAULT NULL,
    error_message VARCHAR(500) DEFAULT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    lock_version INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_proposal_user_idempotency (user_id, idempotency_key),
    UNIQUE KEY uk_proposal_user_approval (user_id, approval_idempotency_key),
    UNIQUE KEY uk_proposal_user_execution (user_id, execution_idempotency_key),
    INDEX idx_proposal_user_bad_case (user_id, bad_case_id, revision_no),
    INDEX idx_proposal_target_version (target_document_id, base_version_no),
    CONSTRAINT fk_proposal_user FOREIGN KEY (user_id) REFERENCES user_account(id),
    CONSTRAINT fk_proposal_bad_case FOREIGN KEY (bad_case_id) REFERENCES ai_bad_case(id),
    CONSTRAINT fk_proposal_target_document FOREIGN KEY (target_document_id)
        REFERENCES knowledge_document(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE knowledge_document_version
    ADD CONSTRAINT fk_document_version_proposal FOREIGN KEY (proposal_id)
        REFERENCES repair_proposal(id);

ALTER TABLE agent_run
    ADD CONSTRAINT fk_agent_run_bad_case FOREIGN KEY (bad_case_id)
        REFERENCES ai_bad_case(id);
