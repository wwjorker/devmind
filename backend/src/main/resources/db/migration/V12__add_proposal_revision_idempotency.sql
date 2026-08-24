ALTER TABLE repair_proposal
    ADD COLUMN revision_idempotency_key VARCHAR(128) DEFAULT NULL AFTER revision_no;

ALTER TABLE repair_proposal
    ADD UNIQUE KEY uk_proposal_user_revision (user_id, revision_idempotency_key);
