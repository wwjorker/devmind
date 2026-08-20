package com.devmind.module.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devmind.common.api.ResultCode;
import com.devmind.common.exception.BizException;
import com.devmind.module.ai.agent.RepairProposalStatus;
import com.devmind.module.ai.entity.RepairProposal;
import com.devmind.module.ai.mapper.RepairProposalMapper;
import com.devmind.module.document.entity.KnowledgeDocument;
import com.devmind.module.document.entity.KnowledgeDocumentVersion;
import com.devmind.module.document.mapper.KnowledgeDocumentMapper;
import com.devmind.module.document.service.DocumentChunkService;
import com.devmind.module.document.service.DocumentVersionOrigin;
import com.devmind.module.document.service.KnowledgeDocumentVersionService;
import com.devmind.module.search.service.ChunkVectorService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class RepairDocumentMutationService {

    private static final int STATUS_ACTIVE = 1;

    private final RepairProposalMapper proposalMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeDocumentVersionService versionService;
    private final DocumentChunkService chunkService;
    private final ChunkVectorService vectorService;
    private final ObjectMapper objectMapper;

    public RepairDocumentMutationService(RepairProposalMapper proposalMapper,
                                         KnowledgeDocumentMapper documentMapper,
                                         KnowledgeDocumentVersionService versionService,
                                         DocumentChunkService chunkService,
                                         ChunkVectorService vectorService,
                                         ObjectMapper objectMapper) {
        this.proposalMapper = proposalMapper;
        this.documentMapper = documentMapper;
        this.versionService = versionService;
        this.chunkService = chunkService;
        this.vectorService = vectorService;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MetadataMutationResult applyApprovedMetadata(Long userId,
                                                        Long proposalId,
                                                        String executionKey) {
        RepairProposal proposal = requireExecutingProposal(userId, proposalId, executionKey);
        KnowledgeDocument document = findOwnedActiveDocument(userId, proposal.getTargetDocumentId());
        if (!Objects.equals(document.getVersionNo(), proposal.getBaseVersionNo())) {
            throw new BizException(ResultCode.CONFLICT,
                    "proposal base version is stale at execution time");
        }
        JsonNode diff = parseObject(proposal.getApprovedDiffJson());
        if (diff.has("tags")) document.setTags(diff.get("tags").textValue());
        if (diff.has("summary")) document.setSummary(diff.get("summary").textValue());
        updateDocumentOrThrowConflict(document);
        versionService.snapshot(
                document,
                DocumentVersionOrigin.REPAIR_PROPOSAL,
                proposal.getId(),
                proposal.getEvidenceJson());
        vectorService.archiveMySqlByDocument(userId, document.getId());
        return new MetadataMutationResult(
                document.getId(),
                document.getVersionNo(),
                chunkService.listActiveChunkEntities(userId, document.getId()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MetadataMutationResult rollbackMetadata(Long userId,
                                                   Long proposalId,
                                                   String executionKey,
                                                   int appliedVersionNo) {
        RepairProposal proposal = requireRecoverableProposal(userId, proposalId, executionKey);
        KnowledgeDocument document = findOwnedActiveDocument(userId, proposal.getTargetDocumentId());
        if (!Objects.equals(document.getVersionNo(), appliedVersionNo)) {
            KnowledgeDocumentVersion currentVersion = versionService.getOwnedVersion(
                    userId, document.getId(), document.getVersionNo());
            if (Objects.equals(currentVersion.getProposalId(), proposalId)
                    && DocumentVersionOrigin.REPAIR_ROLLBACK.name()
                    .equals(currentVersion.getOrigin())) {
                return new MetadataMutationResult(
                        document.getId(),
                        document.getVersionNo(),
                        chunkService.listActiveChunkEntities(userId, document.getId()));
            }
            throw new BizException(ResultCode.CONFLICT,
                    "document changed after repair; automatic rollback is unsafe");
        }
        KnowledgeDocumentVersion applied = versionService.getOwnedVersion(
                userId, document.getId(), appliedVersionNo);
        if (!Objects.equals(applied.getProposalId(), proposalId)
                || !DocumentVersionOrigin.REPAIR_PROPOSAL.name().equals(applied.getOrigin())) {
            throw new BizException(ResultCode.CONFLICT,
                    "current document version was not created by this proposal");
        }
        KnowledgeDocumentVersion base = versionService.getOwnedVersion(
                userId, document.getId(), proposal.getBaseVersionNo());
        document.setTags(base.getTags());
        document.setSummary(base.getSummary());
        updateDocumentOrThrowConflict(document);
        versionService.snapshot(
                document,
                DocumentVersionOrigin.REPAIR_ROLLBACK,
                proposal.getId(),
                proposal.getEvidenceJson());
        vectorService.archiveMySqlByDocument(userId, document.getId());
        return new MetadataMutationResult(
                document.getId(),
                document.getVersionNo(),
                chunkService.listActiveChunkEntities(userId, document.getId()));
    }

    private RepairProposal requireExecutingProposal(Long userId,
                                                    Long proposalId,
                                                    String executionKey) {
        RepairProposal proposal = findOwnedProposal(userId, proposalId);
        if (!RepairProposalStatus.EXECUTING.name().equals(proposal.getStatus())
                || !Objects.equals(proposal.getExecutionIdempotencyKey(), executionKey)) {
            throw new BizException(ResultCode.CONFLICT,
                    "proposal is not reserved for this execution");
        }
        return proposal;
    }

    private RepairProposal requireRecoverableProposal(Long userId,
                                                      Long proposalId,
                                                      String executionKey) {
        RepairProposal proposal = findOwnedProposal(userId, proposalId);
        if (!Objects.equals(proposal.getExecutionIdempotencyKey(), executionKey)
                || !java.util.Set.of(
                        RepairProposalStatus.EXECUTING.name(),
                        RepairProposalStatus.VERIFYING.name(),
                        RepairProposalStatus.FAILED.name()).contains(proposal.getStatus())) {
            throw new BizException(ResultCode.CONFLICT,
                    "proposal is not eligible for rollback");
        }
        return proposal;
    }

    private RepairProposal findOwnedProposal(Long userId, Long proposalId) {
        RepairProposal proposal = proposalMapper.selectOne(
                new LambdaQueryWrapper<RepairProposal>()
                        .eq(RepairProposal::getId, proposalId)
                        .eq(RepairProposal::getUserId, userId));
        if (proposal == null) {
            throw new BizException(ResultCode.NOT_FOUND, "repair proposal not found");
        }
        return proposal;
    }

    private KnowledgeDocument findOwnedActiveDocument(Long userId, Long documentId) {
        KnowledgeDocument document = documentMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeDocument>()
                        .eq(KnowledgeDocument::getId, documentId)
                        .eq(KnowledgeDocument::getUserId, userId)
                        .eq(KnowledgeDocument::getStatus, STATUS_ACTIVE));
        if (document == null) {
            throw new BizException(ResultCode.NOT_FOUND, "target document not found");
        }
        return document;
    }

    private JsonNode parseObject(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node == null || !node.isObject()) {
                throw new BizException(ResultCode.INTERNAL_ERROR,
                        "approved proposal diff is invalid");
            }
            return node;
        } catch (JsonProcessingException ex) {
            throw new BizException(ResultCode.INTERNAL_ERROR,
                    "approved proposal diff is invalid");
        }
    }

    private void updateDocumentOrThrowConflict(KnowledgeDocument document) {
        if (documentMapper.updateById(document) != 1) {
            throw new BizException(ResultCode.CONFLICT,
                    "document changed concurrently; reload the latest version");
        }
    }
}
