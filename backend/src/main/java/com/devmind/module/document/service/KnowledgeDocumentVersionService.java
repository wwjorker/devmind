package com.devmind.module.document.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devmind.common.api.ResultCode;
import com.devmind.common.exception.BizException;
import com.devmind.module.document.entity.KnowledgeDocument;
import com.devmind.module.document.entity.KnowledgeDocumentVersion;
import com.devmind.module.document.mapper.KnowledgeDocumentVersionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

@Service
public class KnowledgeDocumentVersionService {

    private static final int MAX_SOURCE_EVIDENCE_CHARS = 24_000;

    private final KnowledgeDocumentVersionMapper versionMapper;

    public KnowledgeDocumentVersionService(KnowledgeDocumentVersionMapper versionMapper) {
        this.versionMapper = versionMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public KnowledgeDocumentVersion snapshot(KnowledgeDocument document,
                                             DocumentVersionOrigin origin,
                                             Long proposalId,
                                             String sourceEvidenceJson) {
        Objects.requireNonNull(document, "document must not be null");
        Objects.requireNonNull(origin, "origin must not be null");
        requirePositive(document.getId(), "documentId");
        requirePositive(document.getUserId(), "userId");
        if (document.getVersionNo() == null || document.getVersionNo() <= 0) {
            throw new IllegalArgumentException("document versionNo must be positive");
        }
        if (proposalId != null && proposalId <= 0) {
            throw new IllegalArgumentException("proposalId must be positive");
        }
        if (sourceEvidenceJson != null
                && sourceEvidenceJson.length() > MAX_SOURCE_EVIDENCE_CHARS) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "document source evidence is too large");
        }

        KnowledgeDocumentVersion version = new KnowledgeDocumentVersion();
        version.setDocumentId(document.getId());
        version.setUserId(document.getUserId());
        version.setVersionNo(document.getVersionNo());
        version.setTitle(document.getTitle());
        version.setContent(document.getContent());
        version.setSourceType(document.getSourceType());
        version.setTags(document.getTags());
        version.setSummary(document.getSummary());
        version.setDocumentStatus(document.getStatus());
        version.setOrigin(origin.name());
        version.setSourceEvidenceJson(
                StringUtils.hasText(sourceEvidenceJson) ? sourceEvidenceJson : null);
        version.setProposalId(proposalId);
        versionMapper.insert(version);
        return version;
    }

    public KnowledgeDocumentVersion getOwnedVersion(Long userId,
                                                     Long documentId,
                                                     int versionNo) {
        requirePositive(userId, "userId");
        requirePositive(documentId, "documentId");
        if (versionNo <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "versionNo must be positive");
        }
        KnowledgeDocumentVersion version = versionMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeDocumentVersion>()
                        .eq(KnowledgeDocumentVersion::getUserId, userId)
                        .eq(KnowledgeDocumentVersion::getDocumentId, documentId)
                        .eq(KnowledgeDocumentVersion::getVersionNo, versionNo));
        if (version == null) {
            throw new BizException(ResultCode.NOT_FOUND, "document version not found");
        }
        return version;
    }

    public List<KnowledgeDocumentVersion> listOwnedVersions(Long userId, Long documentId) {
        requirePositive(userId, "userId");
        requirePositive(documentId, "documentId");
        return versionMapper.selectList(new LambdaQueryWrapper<KnowledgeDocumentVersion>()
                .eq(KnowledgeDocumentVersion::getUserId, userId)
                .eq(KnowledgeDocumentVersion::getDocumentId, documentId)
                .orderByDesc(KnowledgeDocumentVersion::getVersionNo));
    }

    private void requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, field + " must be positive");
        }
    }
}
