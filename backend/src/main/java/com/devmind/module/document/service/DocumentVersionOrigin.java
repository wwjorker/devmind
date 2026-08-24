package com.devmind.module.document.service;

public enum DocumentVersionOrigin {
    MIGRATION_BASELINE,
    USER_CREATE,
    FILE_IMPORT,
    USER_UPDATE,
    USER_ARCHIVE,
    USER_RESTORE,
    REPAIR_PROPOSAL,
    REPAIR_ROLLBACK
}
