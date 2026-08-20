package com.devmind.module.ai.service;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionAttribute;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowTransactionBoundaryTest {

    private final AnnotationTransactionAttributeSource attributes =
            new AnnotationTransactionAttributeSource();

    @Test
    void shouldKeepModelOrchestrationOutsideTransactions() {
        assertPropagation(AgentOrchestrator.class, "triageAndRoute",
                TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
        assertPropagation(ProposalReviewService.class, "review",
                TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
    }

    @Test
    void shouldCommitWorkflowFactsAndAgentRunTogetherInShortTransactions() {
        assertPropagation(TriageWorkflowPersistenceService.class, "persist",
                TransactionDefinition.PROPAGATION_REQUIRED);
        assertPropagation(ProposalReviewPersistenceService.class,
                "saveDecisionAndCompleteRun", TransactionDefinition.PROPAGATION_REQUIRED);
        assertPropagation(AgentRunPersistenceService.class,
                "markSucceededInCurrentTransaction", TransactionDefinition.PROPAGATION_MANDATORY);
    }

    private void assertPropagation(Class<?> type, String methodName, int expected) {
        Method method = Arrays.stream(type.getMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        TransactionAttribute attribute = attributes.getTransactionAttribute(method, type);
        assertThat(attribute).isNotNull();
        assertThat(attribute.getPropagationBehavior()).isEqualTo(expected);
    }
}
