ALTER TABLE repair_proposal
    ADD COLUMN approval_decision VARCHAR(24) DEFAULT NULL AFTER status;
