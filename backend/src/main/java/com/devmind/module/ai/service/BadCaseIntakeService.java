package com.devmind.module.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devmind.common.api.ResultCode;
import com.devmind.common.exception.BizException;
import com.devmind.module.ai.agent.BadCaseStatus;
import com.devmind.module.ai.entity.AiAskFeedback;
import com.devmind.module.ai.entity.AiAskLog;
import com.devmind.module.ai.entity.AiBadCase;
import com.devmind.module.ai.mapper.AiBadCaseMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class BadCaseIntakeService {

    static final String SOURCE_FEEDBACK = "FEEDBACK";

    private final AiBadCaseMapper badCaseMapper;
    private final ObjectMapper objectMapper;

    public BadCaseIntakeService(AiBadCaseMapper badCaseMapper, ObjectMapper objectMapper) {
        this.badCaseMapper = badCaseMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AiBadCase intakeFeedback(AiAskFeedback feedback, AiAskLog askLog) {
        Objects.requireNonNull(feedback, "feedback must not be null");
        Objects.requireNonNull(askLog, "askLog must not be null");
        if (Boolean.TRUE.equals(feedback.getHelpful())) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "helpful feedback is not a bad case");
        }
        if (!Objects.equals(feedback.getUserId(), askLog.getUserId())
                || !Objects.equals(feedback.getAskLogId(), askLog.getId())) {
            throw new BizException(ResultCode.FORBIDDEN,
                    "feedback and ask log ownership do not match");
        }

        String sourceRef = "ask-log:" + askLog.getId();
        AiBadCase existing = findBySource(feedback.getUserId(), SOURCE_FEEDBACK, sourceRef);
        if (existing != null) {
            return existing;
        }

        AiBadCase badCase = new AiBadCase();
        badCase.setUserId(feedback.getUserId());
        badCase.setSourceType(SOURCE_FEEDBACK);
        badCase.setSourceRef(sourceRef);
        badCase.setFeedbackId(feedback.getId());
        badCase.setAskLogId(askLog.getId());
        badCase.setAskSnapshotJson(serialize(new AskSnapshot(
                askLog.getId(),
                askLog.getQuestion(),
                askLog.getAnswer(),
                askLog.getRetrievalKeyword(),
                askLog.getModelProvider(),
                askLog.getMock(),
                askLog.getPromptTokens(),
                askLog.getCompletionTokens(),
                askLog.getTotalTokens(),
                askLog.getElapsedMs(),
                feedback.getReason(),
                feedback.getExpectedAnswer())));
        badCase.setChunkSnapshotJson(normalizeChunkSnapshot(askLog.getRetrievalSnapshotJson()));
        badCase.setPromptSchemaVersion(askLog.getPromptSchemaVersion());
        badCase.setStatus(BadCaseStatus.NEW.name());
        badCase.setStatusVersion(0);
        try {
            badCaseMapper.insert(badCase);
            return badCase;
        } catch (DuplicateKeyException ex) {
            AiBadCase concurrent = findBySource(
                    feedback.getUserId(), SOURCE_FEEDBACK, sourceRef);
            if (concurrent == null) {
                throw ex;
            }
            return concurrent;
        }
    }

    public AiBadCase getOwned(Long userId, Long badCaseId) {
        AiBadCase badCase = badCaseMapper.selectOne(new LambdaQueryWrapper<AiBadCase>()
                .eq(AiBadCase::getId, badCaseId)
                .eq(AiBadCase::getUserId, userId));
        if (badCase == null) {
            throw new BizException(ResultCode.NOT_FOUND, "bad case not found");
        }
        return badCase;
    }

    private AiBadCase findBySource(Long userId, String sourceType, String sourceRef) {
        return badCaseMapper.selectOne(new LambdaQueryWrapper<AiBadCase>()
                .eq(AiBadCase::getUserId, userId)
                .eq(AiBadCase::getSourceType, sourceType)
                .eq(AiBadCase::getSourceRef, sourceRef));
    }

    private String normalizeChunkSnapshot(String snapshot) {
        return snapshot == null || snapshot.isBlank() ? "[]" : snapshot;
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BizException(ResultCode.INTERNAL_ERROR,
                    "failed to serialize bad-case snapshot");
        }
    }
}
