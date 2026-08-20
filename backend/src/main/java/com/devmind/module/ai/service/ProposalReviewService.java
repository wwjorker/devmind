package com.devmind.module.ai.service;

import com.devmind.common.api.ResultCode;
import com.devmind.common.exception.BizException;
import com.devmind.module.ai.agent.AgentExperimentArm;
import com.devmind.module.ai.agent.AgentModelClient;
import com.devmind.module.ai.agent.AgentToolContext;
import com.devmind.module.ai.agent.ChangeReviewInput;
import com.devmind.module.ai.agent.RepairProposalStatus;
import com.devmind.module.ai.agent.ReviewerDecision;
import com.devmind.module.ai.entity.AgentRun;
import com.devmind.module.ai.entity.AiBadCase;
import com.devmind.module.ai.entity.RepairProposal;
import com.devmind.module.document.entity.KnowledgeDocumentVersion;
import com.devmind.module.document.service.KnowledgeDocumentVersionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class ProposalReviewService {

    private final RepairProposalService proposalService;
    private final BadCaseIntakeService badCaseIntakeService;
    private final KnowledgeDocumentVersionService versionService;
    private final AgentRunPersistenceService runPersistenceService;
    private final ChangeReviewerAgent reviewerAgent;
    private final ProposalReviewPersistenceService reviewPersistenceService;
    private final ObjectMapper objectMapper;

    public ProposalReviewService(RepairProposalService proposalService,
                                 BadCaseIntakeService badCaseIntakeService,
                                 KnowledgeDocumentVersionService versionService,
                                 AgentRunPersistenceService runPersistenceService,
                                 ChangeReviewerAgent reviewerAgent,
                                 ProposalReviewPersistenceService reviewPersistenceService,
                                 ObjectMapper objectMapper) {
        this.proposalService = proposalService;
        this.badCaseIntakeService = badCaseIntakeService;
        this.versionService = versionService;
        this.runPersistenceService = runPersistenceService;
        this.reviewerAgent = reviewerAgent;
        this.reviewPersistenceService = reviewPersistenceService;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RepairProposal review(Long userId,
                                 Long proposalId,
                                 AgentToolContext context,
                                 AgentModelClient modelClient) {
        Objects.requireNonNull(context, "context must not be null");
        if (!Objects.equals(userId, context.userId())) {
            throw new BizException(ResultCode.FORBIDDEN,
                    "review context does not belong to the current user");
        }
        RepairProposal proposal = proposalService.getOwned(userId, proposalId);
        if (!RepairProposalStatus.DRAFT.name().equals(proposal.getStatus())) {
            throw new BizException(ResultCode.CONFLICT,
                    "repair proposal is not awaiting review");
        }
        AiBadCase badCase = badCaseIntakeService.getOwned(userId, proposal.getBadCaseId());
        AgentRun run = runPersistenceService.getOwnedRun(userId, context.runId());
        if (!Objects.equals(run.getBadCaseId(), badCase.getId())
                || !AgentExperimentArm.REVIEWED_MULTI.wireValue().equals(run.getExperimentArm())) {
            throw new BizException(ResultCode.CONFLICT,
                    "review run must belong to this bad case and reviewed-multi arm");
        }

        ChangeReviewInput input = new ChangeReviewInput(
                proposal.getId(),
                badCase.getId(),
                badCase.getRootCause(),
                badCase.getDiagnosisJson(),
                proposal.getProposalType(),
                proposal.getTargetDocumentId(),
                proposal.getBaseVersionNo(),
                proposal.getDiffJson(),
                proposal.getEvidenceJson(),
                proposal.getCounterevidenceJson(),
                proposal.getImpactJson(),
                proposal.getRegressionPlanJson(),
                currentVersionJson(userId, proposal),
                proposal.getRevisionNo());
        try {
            ReviewerDecision decision = reviewerAgent.reviewForWorkflow(
                    context, modelClient, input);
            return reviewPersistenceService.saveDecisionAndCompleteRun(
                    userId, proposalId, context.runId(), decision);
        } catch (RuntimeException ex) {
            runPersistenceService.failRunIfActive(
                    userId,
                    context.runId(),
                    "PROPOSAL_REVIEW_WORKFLOW_FAILED",
                    "proposal review workflow failed");
            throw ex;
        }
    }

    private String currentVersionJson(Long userId, RepairProposal proposal) {
        if (proposal.getTargetDocumentId() == null || proposal.getBaseVersionNo() == null) {
            return "{}";
        }
        KnowledgeDocumentVersion version = versionService.getOwnedVersion(
                userId, proposal.getTargetDocumentId(), proposal.getBaseVersionNo());
        ObjectNode node = objectMapper.createObjectNode();
        node.put("documentId", version.getDocumentId());
        node.put("versionNo", version.getVersionNo());
        putText(node, "title", version.getTitle());
        putText(node, "content", version.getContent());
        putText(node, "sourceType", version.getSourceType());
        putText(node, "tags", version.getTags());
        putText(node, "summary", version.getSummary());
        node.put("documentStatus", version.getDocumentStatus());
        putText(node, "origin", version.getOrigin());
        return serialize(node);
    }

    private void putText(ObjectNode node, String field, String value) {
        if (value == null) node.putNull(field); else node.put(field, value);
    }

    private String serialize(ObjectNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException ex) {
            throw new BizException(ResultCode.INTERNAL_ERROR,
                    "failed to serialize current document version");
        }
    }
}
