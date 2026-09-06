package de.melinadanhier.projectflow.generation.event.listener;

import de.melinadanhier.projectflow.generation.event.AiGenerationRequestedEvent;
import de.melinadanhier.projectflow.generation.service.coordination.AiPlanGenerationCoordinator;
import de.melinadanhier.projectflow.generation.service.workflow.AiGenerationWorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class AiGenerationRequestedEventListener {

    private final AiGenerationWorkflowService workflowService;
    private final AiPlanGenerationCoordinator generationCoordinator;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGenerationRequested(AiGenerationRequestedEvent event) {
        workflowService.claimWork(event.workflowId(), event.runId())
                .ifPresent(generationCoordinator::generateClaimed);
    }
}
