package com.devmind.module.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devmind.common.api.ResultCode;
import com.devmind.common.exception.BizException;
import com.devmind.module.ai.agent.BadCaseStatus;
import com.devmind.module.ai.agent.RepairProposalType;
import com.devmind.module.ai.entity.AiBadCase;
import com.devmind.module.ai.mapper.AiBadCaseMapper;
import com.devmind.module.document.entity.KnowledgeDocument;
import com.devmind.module.document.entity.KnowledgeDocumentVersion;
import com.devmind.module.document.mapper.KnowledgeDocumentMapper;
import com.devmind.module.document.service.KnowledgeDocumentVersionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class ProposalValidator {

    private static final Set<String> METADATA_DIFF_FIELDS = Set.of("tags", "summary");
    private static final Set<String> DOCUMENT_DRAFT_FIELDS =
            Set.of("title", "content", "sourceType", "tags", "summary");
    private static final Set<String> EVIDENCE_FIELDS = Set.of(
            "kind", "documentId", "documentVersionNo", "trustedSourceId", "excerpt", "claim");
    private static final Set<String> TRUSTED_SOURCE_FIELDS =
            Set.of("sourceId", "title", "content", "origin");
    private static final Set<String> IMPACT_FIELDS = Set.of("summary", "risk", "affectedQueries");
    private static final Set<String> REGRESSION_FIELDS =
            Set.of("targetQuestion", "relatedKeywords", "fullDatasetVersion");
    private static final int STATUS_ACTIVE = 1;

    private final AiBadCaseMapper badCaseMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeDocumentVersionService versionService;
    private final ObjectMapper objectMapper;

    public ProposalValidator(AiBadCaseMapper badCaseMapper,
                             KnowledgeDocumentMapper documentMapper,
                             KnowledgeDocumentVersionService versionService,
                             ObjectMapper objectMapper) {
        this.badCaseMapper = badCaseMapper;
        this.documentMapper = documentMapper;
        this.versionService = versionService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ValidatedRepairProposal validate(Long userId,
                                            Long badCaseId,
                                            RepairProposalDraft draft) {
        requirePositive(userId, "userId");
        requirePositive(badCaseId, "badCaseId");
        Objects.requireNonNull(draft, "proposal draft must not be null");
        Objects.requireNonNull(draft.type(), "proposal type must not be null");

        AiBadCase badCase = findOwnedBadCase(userId, badCaseId);
        if (!Set.of(
                BadCaseStatus.TRIAGED.name(),
                BadCaseStatus.REVIEWED.name(),
                BadCaseStatus.AWAITING_APPROVAL.name())
                .contains(badCase.getStatus())) {
            throw conflict("bad case status does not allow proposal validation");
        }

        JsonNode diff = parseObject(draft.diffJson(), "proposal diff", 24_000);
        ArrayNode evidence = parseArray(draft.evidenceJson(), "proposal evidence", 1, 10, 24_000);
        ArrayNode counterevidence = parseArray(
                defaultJson(draft.counterevidenceJson(), "[]"),
                "proposal counterevidence", 0, 10, 24_000);
        JsonNode impact = parseObject(draft.impactJson(), "proposal impact", 8_000);
        JsonNode regressionPlan = parseObject(
                draft.regressionPlanJson(), "proposal regression plan", 8_000);

        Map<String, TrustedSource> trustedSources = parseTrustedSources(badCase.getTrustedSourceJson());
        EvidenceSummary evidenceSummary = validateEvidence(userId, evidence, trustedSources);
        validateEvidence(userId, counterevidence, trustedSources);
        validateImpact(impact);
        validateRegressionPlan(regressionPlan);

        if (draft.type() == RepairProposalType.METADATA_PATCH) {
            validateMetadataPatch(userId, badCase, draft, diff, evidenceSummary);
        } else if (draft.type() == RepairProposalType.DOCUMENT_DRAFT) {
            validateDocumentDraft(badCase, draft, diff, evidenceSummary);
        }

        return new ValidatedRepairProposal(
                serialize(diff),
                serialize(evidence),
                serialize(counterevidence),
                serialize(impact),
                serialize(regressionPlan));
    }

    private void validateMetadataPatch(Long userId,
                                       AiBadCase badCase,
                                       RepairProposalDraft draft,
                                       JsonNode diff,
                                       EvidenceSummary evidenceSummary) {
        if (!"knowledge_exists_not_retrieved".equals(badCase.getRootCause())) {
            throw badRequest("metadata patch is not allowed for this root cause");
        }
        requirePositive(draft.targetDocumentId(), "targetDocumentId");
        if (draft.baseVersionNo() == null || draft.baseVersionNo() <= 0) {
            throw badRequest("baseVersionNo must be positive");
        }
        requireOnlyFields(diff, METADATA_DIFF_FIELDS, "metadata patch");
        if (diff.isEmpty()) {
            throw badRequest("metadata patch must change tags or summary");
        }
        String tags = optionalText(diff, "tags", 255);
        String summary = optionalText(diff, "summary", 500);

        KnowledgeDocument document = documentMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeDocument>()
                        .eq(KnowledgeDocument::getId, draft.targetDocumentId())
                        .eq(KnowledgeDocument::getUserId, userId)
                        .eq(KnowledgeDocument::getStatus, STATUS_ACTIVE));
        if (document == null) {
            throw new BizException(ResultCode.NOT_FOUND, "target document not found");
        }
        if (!Objects.equals(document.getVersionNo(), draft.baseVersionNo())) {
            throw conflict("proposal base version is stale");
        }
        versionService.getOwnedVersion(userId, draft.targetDocumentId(), draft.baseVersionNo());
        if (Objects.equals(tags, document.getTags()) && Objects.equals(summary, document.getSummary())) {
            throw badRequest("metadata patch does not change the target document");
        }
        String citationKey = versionKey(draft.targetDocumentId(), draft.baseVersionNo());
        if (!evidenceSummary.documentVersions().contains(citationKey)) {
            throw badRequest("metadata patch must cite its target base version");
        }
    }

    private void validateDocumentDraft(AiBadCase badCase,
                                       RepairProposalDraft draft,
                                       JsonNode diff,
                                       EvidenceSummary evidenceSummary) {
        if (!"knowledge_missing".equals(badCase.getRootCause())) {
            throw badRequest("document draft is not allowed for this root cause");
        }
        if (draft.targetDocumentId() != null || draft.baseVersionNo() != null) {
            throw badRequest("document draft cannot target an existing document version");
        }
        requireOnlyFields(diff, DOCUMENT_DRAFT_FIELDS, "document draft");
        requiredText(diff, "title", 120);
        requiredText(diff, "content", 20_000);
        requiredText(diff, "sourceType", 32);
        optionalText(diff, "tags", 255);
        optionalText(diff, "summary", 500);
        if (!evidenceSummary.hasTrustedUserSource()) {
            throw badRequest("document draft requires user-supplied trusted source evidence");
        }
    }

    private EvidenceSummary validateEvidence(Long userId,
                                             ArrayNode evidence,
                                             Map<String, TrustedSource> trustedSources) {
        Set<String> documentVersions = new HashSet<>();
        boolean hasTrustedUserSource = false;
        for (JsonNode item : evidence) {
            requireObject(item, "proposal evidence item");
            requireOnlyFields(item, EVIDENCE_FIELDS, "proposal evidence item");
            String kind = requiredText(item, "kind", 32);
            String excerpt = requiredText(item, "excerpt", 500);
            requiredText(item, "claim", 500);
            if ("DOCUMENT_VERSION".equals(kind)) {
                long documentId = requiredPositiveLong(item, "documentId");
                int versionNo = requiredPositiveInt(item, "documentVersionNo");
                requireAbsent(item, "trustedSourceId");
                KnowledgeDocumentVersion version = versionService.getOwnedVersion(
                        userId, documentId, versionNo);
                if (!versionContains(version, excerpt)) {
                    throw badRequest("document-version evidence excerpt is not present in its source");
                }
                documentVersions.add(versionKey(documentId, versionNo));
            } else if ("TRUSTED_USER_SOURCE".equals(kind)) {
                requireAbsent(item, "documentId");
                requireAbsent(item, "documentVersionNo");
                String sourceId = requiredText(item, "trustedSourceId", 128);
                TrustedSource source = trustedSources.get(sourceId);
                if (source == null || !source.content().contains(excerpt)) {
                    throw badRequest("trusted-source evidence is missing or does not contain excerpt");
                }
                hasTrustedUserSource = true;
            } else {
                throw badRequest("unsupported proposal evidence kind: " + kind);
            }
        }
        return new EvidenceSummary(Set.copyOf(documentVersions), hasTrustedUserSource);
    }

    private Map<String, TrustedSource> parseTrustedSources(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        ArrayNode sources = parseArray(json, "trusted sources", 1, 10, 24_000);
        Map<String, TrustedSource> result = new HashMap<>();
        for (JsonNode source : sources) {
            requireObject(source, "trusted source");
            requireOnlyFields(source, TRUSTED_SOURCE_FIELDS, "trusted source");
            String sourceId = requiredText(source, "sourceId", 128);
            String title = requiredText(source, "title", 200);
            String content = requiredText(source, "content", 20_000);
            String origin = requiredText(source, "origin", 32);
            if (!"USER_SUPPLIED".equals(origin)) {
                throw badRequest("trusted source origin must be USER_SUPPLIED");
            }
            if (result.put(sourceId, new TrustedSource(title, content)) != null) {
                throw badRequest("trusted source IDs must be unique");
            }
        }
        return Map.copyOf(result);
    }

    private void validateImpact(JsonNode impact) {
        requireOnlyFields(impact, IMPACT_FIELDS, "proposal impact");
        requiredText(impact, "summary", 1_000);
        String risk = requiredText(impact, "risk", 16);
        if (!Set.of("LOW", "MEDIUM", "HIGH").contains(risk)) {
            throw badRequest("proposal impact risk must be LOW, MEDIUM, or HIGH");
        }
        validateStringArray(impact.get("affectedQueries"), "affectedQueries", 0, 20, 500);
    }

    private void validateRegressionPlan(JsonNode plan) {
        requireOnlyFields(plan, REGRESSION_FIELDS, "proposal regression plan");
        requiredText(plan, "targetQuestion", 500);
        requiredText(plan, "fullDatasetVersion", 128);
        validateStringArray(plan.get("relatedKeywords"), "relatedKeywords", 0, 10, 128);
    }

    private AiBadCase findOwnedBadCase(Long userId, Long badCaseId) {
        AiBadCase badCase = badCaseMapper.selectOne(new LambdaQueryWrapper<AiBadCase>()
                .eq(AiBadCase::getId, badCaseId)
                .eq(AiBadCase::getUserId, userId));
        if (badCase == null) {
            throw new BizException(ResultCode.NOT_FOUND, "bad case not found");
        }
        return badCase;
    }

    private JsonNode parseObject(String json, String label, int maxChars) {
        JsonNode node = parseJson(json, label, maxChars);
        requireObject(node, label);
        return node;
    }

    private ArrayNode parseArray(String json,
                                 String label,
                                 int minItems,
                                 int maxItems,
                                 int maxChars) {
        JsonNode node = parseJson(json, label, maxChars);
        if (!node.isArray() || node.size() < minItems || node.size() > maxItems) {
            throw badRequest(label + " must contain " + minItems + " to " + maxItems + " items");
        }
        return (ArrayNode) node;
    }

    private JsonNode parseJson(String json, String label, int maxChars) {
        if (!StringUtils.hasText(json) || json.length() > maxChars) {
            throw badRequest(label + " must be non-blank and bounded");
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException ex) {
            throw badRequest(label + " must be valid JSON");
        }
    }

    private void requireObject(JsonNode node, String label) {
        if (node == null || !node.isObject()) {
            throw badRequest(label + " must be a JSON object");
        }
    }

    private void requireOnlyFields(JsonNode node, Set<String> allowed, String label) {
        Iterator<String> fields = node.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (!allowed.contains(field)) {
                throw badRequest(label + " contains unsupported field: " + field);
            }
        }
    }

    private String requiredText(JsonNode node, String field, int maxLength) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()
                || value.textValue().length() > maxLength) {
            throw badRequest(field + " must be a non-blank bounded string");
        }
        return value.textValue().trim();
    }

    private String optionalText(JsonNode node, String field, int maxLength) {
        JsonNode value = node.get(field);
        if (value == null) {
            return null;
        }
        if (!value.isTextual() || value.textValue().length() > maxLength) {
            throw badRequest(field + " must be a bounded string");
        }
        return value.textValue().trim();
    }

    private long requiredPositiveLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()
                || value.longValue() <= 0) {
            throw badRequest(field + " must be a positive integer");
        }
        return value.longValue();
    }

    private int requiredPositiveInt(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()
                || value.intValue() <= 0) {
            throw badRequest(field + " must be a positive integer");
        }
        return value.intValue();
    }

    private void requireAbsent(JsonNode node, String field) {
        if (node.has(field) && !node.get(field).isNull()) {
            throw badRequest(field + " is not allowed for this evidence kind");
        }
    }

    private void validateStringArray(JsonNode node,
                                     String field,
                                     int minItems,
                                     int maxItems,
                                     int maxTextLength) {
        if (node == null || !node.isArray() || node.size() < minItems || node.size() > maxItems) {
            throw badRequest(field + " must be a bounded string array");
        }
        for (JsonNode item : node) {
            if (!item.isTextual() || item.textValue().isBlank()
                    || item.textValue().length() > maxTextLength) {
                throw badRequest(field + " contains an invalid item");
            }
        }
    }

    private boolean versionContains(KnowledgeDocumentVersion version, String excerpt) {
        return contains(version.getTitle(), excerpt)
                || contains(version.getContent(), excerpt)
                || contains(version.getTags(), excerpt)
                || contains(version.getSummary(), excerpt);
    }

    private boolean contains(String source, String excerpt) {
        return source != null && source.contains(excerpt);
    }

    private String serialize(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException ex) {
            throw new BizException(ResultCode.INTERNAL_ERROR,
                    "failed to normalize proposal JSON");
        }
    }

    private String defaultJson(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String versionKey(long documentId, int versionNo) {
        return documentId + ":" + versionNo;
    }

    private void requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw badRequest(field + " must be positive");
        }
    }

    private BizException badRequest(String message) {
        return new BizException(ResultCode.BAD_REQUEST, message);
    }

    private BizException conflict(String message) {
        return new BizException(ResultCode.CONFLICT, message);
    }

    private record EvidenceSummary(Set<String> documentVersions,
                                   boolean hasTrustedUserSource) {
    }

    private record TrustedSource(String title, String content) {
    }
}
