package com.devmind.module.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devmind.common.api.ResultCode;
import com.devmind.common.exception.BizException;
import com.devmind.module.ai.agent.RepairProposalStatus;
import com.devmind.module.ai.entity.RepairProposal;
import com.devmind.module.ai.mapper.RepairProposalMapper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class RecoveryService {

    private static final int MAX_RECOVERY_BATCH = 20;

    private final RepairProposalMapper proposalMapper;
    private final RepairExecutor repairExecutor;
    private final Clock clock;

    @Autowired
    public RecoveryService(RepairProposalMapper proposalMapper,
                           RepairExecutor repairExecutor) {
        this(proposalMapper, repairExecutor, Clock.systemDefaultZone());
    }

    RecoveryService(RepairProposalMapper proposalMapper,
                    RepairExecutor repairExecutor,
                    Clock clock) {
        this.proposalMapper = proposalMapper;
        this.repairExecutor = repairExecutor;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<RepairProposal> recoverStale(Long userId, Duration staleAge) {
        if (userId == null || userId <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "userId must be positive");
        }
        if (staleAge == null || staleAge.isNegative() || staleAge.isZero()) {
            throw new BizException(ResultCode.BAD_REQUEST, "staleAge must be positive");
        }
        LocalDateTime cutoff = LocalDateTime.now(clock).minus(staleAge);
        List<RepairProposal> stale = proposalMapper.selectList(
                new LambdaQueryWrapper<RepairProposal>()
                        .eq(RepairProposal::getUserId, userId)
                        .in(RepairProposal::getStatus,
                                RepairProposalStatus.EXECUTING.name(),
                                RepairProposalStatus.VERIFYING.name())
                        .lt(RepairProposal::getUpdatedAt, cutoff)
                        .orderByAsc(RepairProposal::getUpdatedAt)
                        .last("LIMIT " + MAX_RECOVERY_BATCH));
        return stale.stream()
                .map(proposal -> repairExecutor.execute(
                        userId, proposal.getId(), proposal.getExecutionIdempotencyKey()))
                .toList();
    }
}
