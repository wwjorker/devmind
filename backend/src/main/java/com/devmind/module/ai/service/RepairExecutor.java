package com.devmind.module.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devmind.common.api.ResultCode;
import com.devmind.common.exception.BizException;
import com.devmind.module.ai.agent.RepairProposalStatus;
import com.devmind.module.ai.entity.RepairProposal;
import com.devmind.module.document.entity.KnowledgeDocument;
import com.devmind.module.document.entity.KnowledgeDocumentVersion;
import com.devmind.module.document.mapper.KnowledgeDocumentMapper;
import com.devmind.module.document.service.DocumentChunkService;
import com.devmind.module.document.service.DocumentVersionOrigin;
import com.devmind.module.document.service.KnowledgeDocumentVersionService;
import com.devmind.module.search.service.ChunkVectorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;

@Service
public class RepairExecutor {

    private static final Logger log = LoggerFactory.getLogger(RepairExecutor.class);

    private final RepairExecutionStateService stateService;
    private final RepairDocumentMutationService mutationService;
    private final RepairProposalService proposalService;
    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeDocumentVersionService versionService;
    private final DocumentChunkService chunkService;
    private final ChunkVectorService vectorService;
    private final RegressionRunner regressionRunner;

    public RepairExecutor(RepairExecutionStateService stateService,
                          RepairDocumentMutationService mutationService,
                          RepairProposalService proposalService,
                          KnowledgeDocumentMapper documentMapper,
                          KnowledgeDocumentVersionService versionService,
                          DocumentChunkService chunkService,
                          ChunkVectorService vectorService,
                          RegressionRunner regressionRunner) {
        this.stateService = stateService;
        this.mutationService = mutationService;
        this.proposalService = proposalService;
        this.documentMapper = documentMapper;
        this.versionService = versionService;
        this.chunkService = chunkService;
        this.vectorService = vectorService;
        this.regressionRunner = regressionRunner;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RepairProposal execute(Long userId, Long proposalId, String executionKey) {
        RepairProposal proposal = stateService.reserve(userId, proposalId, executionKey);
        RepairProposalStatus status = RepairProposalStatus.valueOf(proposal.getStatus());
        if (Set.of(
                RepairProposalStatus.APPLIED,
                RepairProposalStatus.ROLLED_BACK,
                RepairProposalStatus.INVALIDATED,
                RepairProposalStatus.FAILED).contains(status)) {
            return proposal;
        }

        Integer appliedVersionNo = null;
        RegressionResult regression = null;
        try {
            MetadataMutationResult applied;
            if (status == RepairProposalStatus.EXECUTING) {
                applied = applyOrResumeMutation(userId, proposal, executionKey);
                appliedVersionNo = applied.versionNo();
                vectorService.archiveServingIndexByDocument(userId, applied.documentId());
                proposal = stateService.markVerifying(
                        userId, proposalId, executionKey, appliedVersionNo);
            } else {
                appliedVersionNo = requireAppliedVersion(proposal);
                applied = resumeAppliedMutation(userId, proposal, appliedVersionNo);
            }

            vectorService.rebuildVectors(userId, applied.documentId(), applied.activeChunks());
            proposal = proposalService.getOwned(userId, proposalId);
            regression = regressionRunner.runTargetRetrieval(userId, proposal);
            if (!regression.passed()) {
                throw new BizException(ResultCode.CONFLICT,
                        "target retrieval regression did not pass");
            }
            return stateService.markApplied(
                    userId, proposalId, executionKey, appliedVersionNo, regression);
        } catch (RuntimeException executionFailure) {
            String message = safeMessage(executionFailure);
            if (appliedVersionNo == null) {
                return stateService.markFailed(
                        userId, proposalId, executionKey,
                        "EXECUTION_FAILED_BEFORE_WRITE", message);
            }
            try {
                MetadataMutationResult rolledBack = mutationService.rollbackMetadata(
                        userId, proposalId, executionKey, appliedVersionNo);
                boolean indexRecoveryRequired = false;
                try {
                    vectorService.archiveServingIndexByDocument(userId, rolledBack.documentId());
                    vectorService.rebuildVectors(
                            userId, rolledBack.documentId(), rolledBack.activeChunks());
                } catch (RuntimeException rollbackIndexFailure) {
                    indexRecoveryRequired = true;
                    log.warn("Repair rollback restored MySQL source but index rebuild failed. "
                                    + "userId={}, proposalId={}, documentId={}",
                            userId, proposalId, rolledBack.documentId(), rollbackIndexFailure);
                }
                return stateService.markRolledBack(
                        userId,
                        proposalId,
                        executionKey,
                        appliedVersionNo,
                        rolledBack.versionNo(),
                        regression,
                        message,
                        indexRecoveryRequired);
            } catch (RuntimeException rollbackFailure) {
                log.error("Repair execution failed and automatic rollback was unsafe. "
                                + "userId={}, proposalId={}",
                        userId, proposalId, rollbackFailure);
                return stateService.markFailed(
                        userId,
                        proposalId,
                        executionKey,
                        "ROLLBACK_FAILED",
                        message + "; rollback: " + safeMessage(rollbackFailure));
            }
        }
    }

    private MetadataMutationResult applyOrResumeMutation(Long userId,
                                                         RepairProposal proposal,
                                                         String executionKey) {
        KnowledgeDocument current = findOwnedDocument(userId, proposal.getTargetDocumentId());
        if (current == null) {
            throw new BizException(ResultCode.NOT_FOUND, "target document not found");
        }
        if (Objects.equals(current.getVersionNo(), proposal.getBaseVersionNo())) {
            return mutationService.applyApprovedMetadata(
                    userId, proposal.getId(), executionKey);
        }
        KnowledgeDocumentVersion version = versionService.getOwnedVersion(
                userId, current.getId(), current.getVersionNo());
        if (!Objects.equals(version.getProposalId(), proposal.getId())
                || !DocumentVersionOrigin.REPAIR_PROPOSAL.name().equals(version.getOrigin())) {
            throw new BizException(ResultCode.CONFLICT,
                    "document changed outside this repair execution");
        }
        return new MetadataMutationResult(
                current.getId(),
                current.getVersionNo(),
                chunkService.listActiveChunkEntities(userId, current.getId()));
    }

    private MetadataMutationResult resumeAppliedMutation(Long userId,
                                                         RepairProposal proposal,
                                                         int appliedVersionNo) {
        KnowledgeDocument current = findOwnedDocument(userId, proposal.getTargetDocumentId());
        if (current == null
                || !Objects.equals(current.getVersionNo(), appliedVersionNo)) {
            throw new BizException(ResultCode.CONFLICT,
                    "document changed while repair verification was pending");
        }
        KnowledgeDocumentVersion version = versionService.getOwnedVersion(
                userId, current.getId(), appliedVersionNo);
        if (!Objects.equals(version.getProposalId(), proposal.getId())
                || !DocumentVersionOrigin.REPAIR_PROPOSAL.name().equals(version.getOrigin())) {
            throw new BizException(ResultCode.CONFLICT,
                    "verification version does not belong to this proposal");
        }
        return new MetadataMutationResult(
                current.getId(),
                current.getVersionNo(),
                chunkService.listActiveChunkEntities(userId, current.getId()));
    }

    private KnowledgeDocument findOwnedDocument(Long userId, Long documentId) {
        return documentMapper.selectOne(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getId, documentId)
                .eq(KnowledgeDocument::getUserId, userId));
    }

    private int requireAppliedVersion(RepairProposal proposal) {
        Integer version = stateService.appliedVersion(proposal);
        if (version == null || version <= 0) {
            throw new BizException(ResultCode.INTERNAL_ERROR,
                    "repair verification has no applied document version");
        }
        return version;
    }

    private String safeMessage(RuntimeException ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) return ex.getClass().getSimpleName();
        return message.length() <= 300 ? message : message.substring(0, 300);
    }
}
