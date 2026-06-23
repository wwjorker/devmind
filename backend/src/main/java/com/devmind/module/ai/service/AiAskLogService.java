package com.devmind.module.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.devmind.common.api.PageResult;
import com.devmind.module.ai.entity.AiAskLog;
import com.devmind.module.ai.mapper.AiAskLogMapper;
import com.devmind.module.ai.vo.AskLogResponse;
import com.devmind.module.search.vo.ChunkSearchResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AiAskLogService {

    private static final int STATUS_SUCCESS = 1;
    private static final long MAX_PAGE_SIZE = 50;

    private final AiAskLogMapper askLogMapper;

    public AiAskLogService(AiAskLogMapper askLogMapper) {
        this.askLogMapper = askLogMapper;
    }

    @Transactional
    public Long saveSuccessLog(Long userId,
                               String question,
                               String retrievalKeyword,
                               String promptPreview,
                               String answer,
                               String modelProvider,
                               boolean mock,
                               Integer promptTokens,
                               Integer completionTokens,
                               Integer totalTokens,
                               List<ChunkSearchResponse> retrievedChunks,
                               long elapsedMs) {
        AiAskLog log = new AiAskLog();
        log.setUserId(userId);
        log.setQuestion(question);
        log.setRetrievalKeyword(retrievalKeyword);
        log.setPromptPreview(promptPreview);
        log.setAnswer(answer);
        log.setModelProvider(modelProvider);
        log.setMock(mock);
        log.setPromptTokens(promptTokens);
        log.setCompletionTokens(completionTokens);
        log.setTotalTokens(totalTokens);
        log.setRetrievedChunkCount(retrievedChunks.size());
        log.setRetrievedChunkIds(toChunkIds(retrievedChunks));
        log.setElapsedMs(elapsedMs);
        log.setStatus(STATUS_SUCCESS);
        askLogMapper.insert(log);
        return log.getId();
    }

    public PageResult<AskLogResponse> page(Long userId, long pageNo, long pageSize) {
        long safePageNo = Math.max(pageNo, 1);
        long safePageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);

        LambdaQueryWrapper<AiAskLog> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(AiAskLog::getUserId, userId)
                .orderByDesc(AiAskLog::getCreatedAt)
                .orderByDesc(AiAskLog::getId);

        Page<AiAskLog> page = askLogMapper.selectPage(new Page<>(safePageNo, safePageSize), queryWrapper);
        List<AskLogResponse> records = page.getRecords().stream()
                .map(this::toResponse)
                .toList();

        return new PageResult<>(page.getCurrent(), page.getSize(), page.getTotal(), records);
    }

    private String toChunkIds(List<ChunkSearchResponse> chunks) {
        return chunks.stream()
                .map(chunk -> String.valueOf(chunk.getChunkId()))
                .collect(Collectors.joining(","));
    }

    private AskLogResponse toResponse(AiAskLog log) {
        return new AskLogResponse(
                log.getId(),
                log.getQuestion(),
                log.getRetrievalKeyword(),
                log.getPromptPreview(),
                log.getAnswer(),
                log.getModelProvider(),
                log.getMock(),
                log.getPromptTokens(),
                log.getCompletionTokens(),
                log.getTotalTokens(),
                log.getRetrievedChunkCount(),
                log.getRetrievedChunkIds(),
                log.getElapsedMs(),
                log.getStatus(),
                log.getCreatedAt()
        );
    }
}
