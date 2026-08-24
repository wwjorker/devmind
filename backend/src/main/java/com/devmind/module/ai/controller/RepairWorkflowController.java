package com.devmind.module.ai.controller;

import com.devmind.common.api.Result;
import com.devmind.common.security.AuthenticatedUser;
import com.devmind.module.ai.agent.DeepSeekAgentModelClient;
import com.devmind.module.ai.dto.ProposalDecisionRequest;
import com.devmind.module.ai.dto.WorkflowKeyRequest;
import com.devmind.module.ai.service.ProposalApprovalCommand;
import com.devmind.module.ai.service.RepairWorkflowActionService;
import com.devmind.module.ai.service.RepairWorkflowQueryService;
import com.devmind.module.ai.vo.RepairCaseDetailResponse;
import com.devmind.module.ai.vo.RepairCaseSummaryResponse;
import com.devmind.module.ai.vo.RepairProposalResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ai/repair")
public class RepairWorkflowController {

    private final RepairWorkflowQueryService queryService;
    private final RepairWorkflowActionService actionService;
    private final DeepSeekAgentModelClient agentModelClient;

    public RepairWorkflowController(RepairWorkflowQueryService queryService,
                                    RepairWorkflowActionService actionService,
                                    DeepSeekAgentModelClient agentModelClient) {
        this.queryService = queryService;
        this.actionService = actionService;
        this.agentModelClient = agentModelClient;
    }

    @GetMapping("/cases")
    public Result<List<RepairCaseSummaryResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String status) {
        return Result.success(queryService.list(user.userId(), status));
    }

    @GetMapping("/cases/{badCaseId}")
    public Result<RepairCaseDetailResponse> detail(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long badCaseId) {
        return Result.success(queryService.detail(user.userId(), badCaseId));
    }

    @PostMapping("/cases/{badCaseId}/triage")
    public Result<RepairCaseDetailResponse> triage(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long badCaseId,
            @Valid @RequestBody WorkflowKeyRequest request) {
        actionService.triage(
                user.userId(), badCaseId, request.idempotencyKey(), agentModelClient);
        return Result.success(queryService.detail(user.userId(), badCaseId));
    }

    @PostMapping("/proposals/{proposalId}/review")
    public Result<RepairProposalResponse> review(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long proposalId,
            @Valid @RequestBody WorkflowKeyRequest request) {
        actionService.review(
                user.userId(), proposalId, request.idempotencyKey(), agentModelClient);
        return Result.success(queryService.proposal(user.userId(), proposalId));
    }

    @PostMapping("/proposals/{proposalId}/decision")
    public Result<RepairProposalResponse> decide(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long proposalId,
            @Valid @RequestBody ProposalDecisionRequest request) {
        actionService.decide(
                user.userId(),
                proposalId,
                new ProposalApprovalCommand(
                        request.decision(), request.idempotencyKey(),
                        request.editedDiffJson(), request.comment()));
        return Result.success(queryService.proposal(user.userId(), proposalId));
    }

    @PostMapping("/proposals/{proposalId}/execute")
    public Result<RepairProposalResponse> execute(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long proposalId,
            @Valid @RequestBody WorkflowKeyRequest request) {
        actionService.execute(user.userId(), proposalId, request.idempotencyKey());
        return Result.success(queryService.proposal(user.userId(), proposalId));
    }
}
