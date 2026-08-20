package com.devmind.module.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devmind.common.api.ResultCode;
import com.devmind.common.exception.BizException;
import com.devmind.module.ai.agent.BadCaseStatus;
import com.devmind.module.ai.agent.TriageDiagnosis;
import com.devmind.module.ai.entity.AiBadCase;
import com.devmind.module.ai.mapper.AiBadCaseMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class BadCaseStateService {

    private final AiBadCaseMapper badCaseMapper;
    private final ObjectMapper objectMapper;

    public BadCaseStateService(AiBadCaseMapper badCaseMapper, ObjectMapper objectMapper) {
        this.badCaseMapper = badCaseMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AiBadCase recordTriage(Long userId, Long badCaseId, TriageDiagnosis diagnosis) {
        Objects.requireNonNull(diagnosis, "diagnosis must not be null");
        AiBadCase badCase = findOwned(userId, badCaseId);
        requireTransition(badCase, BadCaseStatus.NEW, BadCaseStatus.TRIAGED);
        badCase.setRootCause(diagnosis.rootCause().wireValue());
        badCase.setDiagnosisJson(serialize(diagnosis));
        badCase.setStatus(BadCaseStatus.TRIAGED.name());
        updateOrThrowConflict(badCase);
        return badCase;
    }

    @Transactional
    public AiBadCase transition(Long userId,
                                Long badCaseId,
                                BadCaseStatus expected,
                                BadCaseStatus target) {
        Objects.requireNonNull(expected, "expected status must not be null");
        Objects.requireNonNull(target, "target status must not be null");
        AiBadCase badCase = findOwned(userId, badCaseId);
        requireTransition(badCase, expected, target);
        badCase.setStatus(target.name());
        updateOrThrowConflict(badCase);
        return badCase;
    }

    private AiBadCase findOwned(Long userId, Long badCaseId) {
        AiBadCase badCase = badCaseMapper.selectOne(new LambdaQueryWrapper<AiBadCase>()
                .eq(AiBadCase::getId, badCaseId)
                .eq(AiBadCase::getUserId, userId));
        if (badCase == null) {
            throw new BizException(ResultCode.NOT_FOUND, "bad case not found");
        }
        return badCase;
    }

    private void requireTransition(AiBadCase badCase,
                                   BadCaseStatus expected,
                                   BadCaseStatus target) {
        BadCaseStatus current = BadCaseStatus.valueOf(badCase.getStatus());
        if (current != expected) {
            throw new BizException(ResultCode.CONFLICT,
                    "bad case status changed; expected " + expected + " but was " + current);
        }
        if (!current.canTransitionTo(target)) {
            throw new BizException(ResultCode.CONFLICT,
                    "invalid bad case transition: " + current + " -> " + target);
        }
    }

    private void updateOrThrowConflict(AiBadCase badCase) {
        if (badCaseMapper.updateById(badCase) != 1) {
            throw new BizException(ResultCode.CONFLICT,
                    "bad case changed concurrently; reload the latest state");
        }
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BizException(ResultCode.INTERNAL_ERROR,
                    "failed to serialize bad-case diagnosis");
        }
    }
}
