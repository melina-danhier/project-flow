package de.melinadanhier.projectflow.generation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class AiGenerationProcessor {

    private final AiWorkflowStateService workflowStateService;
    private final AiPlanGenerationCoordinator generationCoordinator;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void startAfterCommit(AiGenerationRequestedEvent event) {
        workflowStateService.claimGenerationWork(event.workflowId())
                .ifPresent(generationCoordinator::generateClaimed);
    }
}
