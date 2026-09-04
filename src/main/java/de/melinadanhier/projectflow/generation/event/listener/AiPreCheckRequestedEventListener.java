package de.melinadanhier.projectflow.generation.event.listener;

import de.melinadanhier.projectflow.generation.event.AiPreCheckRequestedEvent;
import de.melinadanhier.projectflow.generation.service.precheck.AiPreCheckProcessor;
import de.melinadanhier.projectflow.generation.service.workflow.AiPreCheckWorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class AiPreCheckRequestedEventListener {

    private final AiPreCheckWorkflowService workflowService;
    private final AiPreCheckProcessor processor;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPreCheckRequested(AiPreCheckRequestedEvent event) {
        workflowService.claimAndReadSnapshot(event.workflowId(), event.runId())
                .ifPresent(snapshot -> processor.processClaimed(
                        event.workflowId(), event.runId(), snapshot));
    }
}
