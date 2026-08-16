package com.devmind.module.ai.agent;

public enum TriageRootCause {
    KNOWLEDGE_EXISTS_NOT_RETRIEVED(
            "knowledge_exists_not_retrieved", TriageRoute.RETRIEVAL_METADATA_PROPOSAL),
    KNOWLEDGE_TRULY_MISSING(
            "knowledge_truly_missing", TriageRoute.KNOWLEDGE_GAP_TICKET),
    SOURCES_CONFLICT_OR_STALE(
            "sources_conflict_or_stale", TriageRoute.HUMAN_SOURCE_CONFLICT_REVIEW),
    CORRECT_EVIDENCE_ANSWER_WRONG(
            "correct_evidence_answer_wrong", TriageRoute.ANSWER_POLICY_REVIEW),
    EXPECTED_ANSWER_WRONG(
            "expected_answer_wrong", TriageRoute.EXPECTED_ANSWER_CORRECTION),
    OUT_OF_KNOWLEDGE_BASE_SCOPE(
            "out_of_knowledge_base_scope", TriageRoute.NO_ACTION_OUT_OF_SCOPE);

    private final String wireValue;
    private final TriageRoute requiredRoute;

    TriageRootCause(String wireValue, TriageRoute requiredRoute) {
        this.wireValue = wireValue;
        this.requiredRoute = requiredRoute;
    }

    public String wireValue() {
        return wireValue;
    }

    public TriageRoute requiredRoute() {
        return requiredRoute;
    }

    public static TriageRootCause fromWireValue(String value) {
        for (TriageRootCause rootCause : values()) {
            if (rootCause.wireValue.equals(value)) {
                return rootCause;
            }
        }
        throw new IllegalArgumentException("unsupported triage root cause: " + value);
    }
}
