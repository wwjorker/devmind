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

    public static RepairProposalType fromWireValue(String value) {
        if (value == null) throw new IllegalArgumentException("proposal type is required");
        return switch (value) {
            case "metadata_patch", "METADATA_PATCH" -> METADATA_PATCH;
            case "document_draft", "DOCUMENT_DRAFT" -> DOCUMENT_DRAFT;
            default -> throw new IllegalArgumentException("unsupported proposal type: " + value);
        };
    }
}
