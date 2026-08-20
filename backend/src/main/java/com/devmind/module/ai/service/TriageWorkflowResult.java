package com.devmind.module.ai.service;

import com.devmind.module.ai.entity.AgentRun;
import com.devmind.module.ai.entity.AiBadCase;
import com.devmind.module.ai.entity.RepairProposal;

public record TriageWorkflowResult(
        AgentRun run,
        AiBadCase badCase,
        RepairProposal proposal
) {
}
