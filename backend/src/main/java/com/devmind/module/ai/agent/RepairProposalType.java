package com.devmind.module.ai.agent;

public enum RepairProposalType {
    METADATA_PATCH(true),
    DOCUMENT_DRAFT(false);

    private final boolean executable;

    RepairProposalType(boolean executable) {
        this.executable = executable;
    }

    public boolean isExecutable() {
        return executable;
    }
}
