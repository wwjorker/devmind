package com.devmind.module.ai.agent;

import java.util.EnumSet;
import java.util.Set;

public enum BadCaseStatus {
    NEW,
    TRIAGED,
    NO_ACTION,
    CONFLICT_PENDING,
    TICKETED,
    PROPOSED,
    REVIEWED,
    AWAITING_APPROVAL,
    APPROVED,
    EXECUTING,
    VERIFYING,
    RESOLVED,
    ROLLED_BACK,
    FAILED;

    public boolean canTransitionTo(BadCaseStatus target) {
        return allowedTargets().contains(target);
    }

    public boolean isTerminal() {
        return allowedTargets().isEmpty();
    }

    private Set<BadCaseStatus> allowedTargets() {
        return switch (this) {
            case NEW -> EnumSet.of(TRIAGED, FAILED);
            case TRIAGED -> EnumSet.of(
                    NO_ACTION, CONFLICT_PENDING, TICKETED, PROPOSED, FAILED);
            case PROPOSED -> EnumSet.of(REVIEWED, FAILED);
            case REVIEWED -> EnumSet.of(PROPOSED, AWAITING_APPROVAL, NO_ACTION, FAILED);
            case AWAITING_APPROVAL -> EnumSet.of(APPROVED, NO_ACTION, FAILED);
            case APPROVED -> EnumSet.of(EXECUTING, FAILED);
            case EXECUTING -> EnumSet.of(VERIFYING, ROLLED_BACK, FAILED);
            case VERIFYING -> EnumSet.of(RESOLVED, ROLLED_BACK, FAILED);
            case NO_ACTION, CONFLICT_PENDING, TICKETED, RESOLVED, ROLLED_BACK, FAILED ->
                    EnumSet.noneOf(BadCaseStatus.class);
        };
    }
}
