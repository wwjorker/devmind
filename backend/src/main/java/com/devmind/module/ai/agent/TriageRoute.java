package com.devmind.module.ai.agent;

public enum TriageRoute {
    RETRIEVAL_METADATA_PROPOSAL("retrieval_metadata_proposal"),
    KNOWLEDGE_GAP_TICKET("knowledge_gap_ticket"),
    HUMAN_SOURCE_CONFLICT_REVIEW("human_source_conflict_review"),
    ANSWER_POLICY_REVIEW("answer_policy_review"),
    EXPECTED_ANSWER_CORRECTION("expected_answer_correction"),
    NO_ACTION_OUT_OF_SCOPE("no_action_out_of_scope");

    private final String wireValue;

    TriageRoute(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static TriageRoute fromWireValue(String value) {
        for (TriageRoute route : values()) {
            if (route.wireValue.equals(value)) {
                return route;
            }
        }
        throw new IllegalArgumentException("unsupported triage route: " + value);
    }
}
