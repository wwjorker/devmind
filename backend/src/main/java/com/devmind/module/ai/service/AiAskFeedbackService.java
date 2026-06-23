package com.devmind.module.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.devmind.common.api.PageResult;
import com.devmind.common.api.ResultCode;
import com.devmind.common.exception.BizException;
import com.devmind.module.ai.dto.AskFeedbackRequest;
import com.devmind.module.ai.entity.AiAskFeedback;
import com.devmind.module.ai.entity.AiAskLog;
import com.devmind.module.ai.mapper.AiAskFeedbackMapper;
import com.devmind.module.ai.mapper.AiAskLogMapper;
import com.devmind.module.ai.vo.AskFeedbackResponse;
import com.devmind.module.ai.vo.BadCaseSummaryResponse;
import com.devmind.module.ai.vo.EvaluationSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AiAskFeedbackService {

    private static final int STATUS_ACTIVE = 1;
    private static final long MAX_PAGE_SIZE = 50;
    private static final int DEFAULT_RECENT_BAD_CASE_LIMIT = 5;
    private static final int MAX_RECENT_BAD_CASE_LIMIT = 20;

    private final AiAskFeedbackMapper feedbackMapper;
    private final AiAskLogMapper askLogMapper;

    public AiAskFeedbackService(AiAskFeedbackMapper feedbackMapper,
                                AiAskLogMapper askLogMapper) {
        this.feedbackMapper = feedbackMapper;
        this.askLogMapper = askLogMapper;
    }

    @Transactional
    public AskFeedbackResponse saveFeedback(Long userId, Long askLogId, AskFeedbackRequest request) {
        AiAskLog askLog = askLogMapper.selectOne(new LambdaQueryWrapper<AiAskLog>()
                .eq(AiAskLog::getId, askLogId)
                .eq(AiAskLog::getUserId, userId));
        if (askLog == null) {
            throw new BizException(ResultCode.NOT_FOUND, "ask log not found");
        }

        AiAskFeedback feedback = new AiAskFeedback();
        feedback.setUserId(userId);
        feedback.setAskLogId(askLogId);
        feedback.setHelpful(request.getHelpful());
        feedback.setReason(normalize(request.getReason()));
        feedback.setExpectedAnswer(normalize(request.getExpectedAnswer()));
        feedback.setStatus(STATUS_ACTIVE);
        feedback.setCreatedAt(LocalDateTime.now());
        feedbackMapper.insert(feedback);
        return toResponse(feedback);
    }

    public PageResult<AskFeedbackResponse> page(Long userId, Boolean helpful, long pageNo, long pageSize) {
        long safePageNo = Math.max(pageNo, 1);
        long safePageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);

        LambdaQueryWrapper<AiAskFeedback> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(AiAskFeedback::getUserId, userId)
                .eq(AiAskFeedback::getStatus, STATUS_ACTIVE);
        if (helpful != null) {
            queryWrapper.eq(AiAskFeedback::getHelpful, helpful);
        }
        queryWrapper.orderByDesc(AiAskFeedback::getCreatedAt)
                .orderByDesc(AiAskFeedback::getId);

        Page<AiAskFeedback> page = feedbackMapper.selectPage(new Page<>(safePageNo, safePageSize), queryWrapper);
        List<AskFeedbackResponse> records = page.getRecords().stream()
                .map(this::toResponse)
                .toList();

        return new PageResult<>(page.getCurrent(), page.getSize(), page.getTotal(), records);
    }

    public EvaluationSummaryResponse summary(Long userId, Integer recentLimit) {
        int safeRecentLimit = recentLimit == null
                ? DEFAULT_RECENT_BAD_CASE_LIMIT
                : Math.min(Math.max(recentLimit, 1), MAX_RECENT_BAD_CASE_LIMIT);

        long totalFeedbackCount = feedbackMapper.selectCount(baseUserQuery(userId));
        long helpfulCount = feedbackMapper.selectCount(baseUserQuery(userId)
                .eq(AiAskFeedback::getHelpful, true));
        long badCaseCount = feedbackMapper.selectCount(baseUserQuery(userId)
                .eq(AiAskFeedback::getHelpful, false));
        double badCaseRate = totalFeedbackCount == 0
                ? 0.0
                : roundToFourDecimals((double) badCaseCount / totalFeedbackCount);

        List<AiAskFeedback> recentFeedbacks = feedbackMapper.selectList(baseUserQuery(userId)
                .eq(AiAskFeedback::getHelpful, false)
                .orderByDesc(AiAskFeedback::getCreatedAt)
                .orderByDesc(AiAskFeedback::getId)
                .last("LIMIT " + safeRecentLimit));

        Map<Long, AiAskLog> logMap = loadAskLogMap(recentFeedbacks);
        List<BadCaseSummaryResponse> recentBadCases = recentFeedbacks.stream()
                .map(feedback -> toBadCaseSummary(feedback, logMap.get(feedback.getAskLogId())))
                .toList();

        return new EvaluationSummaryResponse(
                totalFeedbackCount,
                helpfulCount,
                badCaseCount,
                badCaseRate,
                recentBadCases
        );
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private LambdaQueryWrapper<AiAskFeedback> baseUserQuery(Long userId) {
        return new LambdaQueryWrapper<AiAskFeedback>()
                .eq(AiAskFeedback::getUserId, userId)
                .eq(AiAskFeedback::getStatus, STATUS_ACTIVE);
    }

    private Map<Long, AiAskLog> loadAskLogMap(List<AiAskFeedback> feedbacks) {
        List<Long> askLogIds = feedbacks.stream()
                .map(AiAskFeedback::getAskLogId)
                .distinct()
                .toList();
        if (askLogIds.isEmpty()) {
            return Map.of();
        }
        return askLogMapper.selectList(new LambdaQueryWrapper<AiAskLog>()
                        .in(AiAskLog::getId, askLogIds))
                .stream()
                .collect(Collectors.toMap(AiAskLog::getId, Function.identity()));
    }

    private BadCaseSummaryResponse toBadCaseSummary(AiAskFeedback feedback, AiAskLog askLog) {
        String question = askLog == null ? null : askLog.getQuestion();
        return new BadCaseSummaryResponse(
                feedback.getId(),
                feedback.getAskLogId(),
                question,
                feedback.getReason(),
                feedback.getExpectedAnswer(),
                feedback.getCreatedAt()
        );
    }

    private double roundToFourDecimals(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private AskFeedbackResponse toResponse(AiAskFeedback feedback) {
        return new AskFeedbackResponse(
                feedback.getId(),
                feedback.getAskLogId(),
                feedback.getHelpful(),
                feedback.getReason(),
                feedback.getExpectedAnswer(),
                feedback.getStatus(),
                feedback.getCreatedAt()
        );
    }
}
