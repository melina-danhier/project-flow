package de.melinadanhier.projectflow.generation.service;

import de.melinadanhier.projectflow.generation.client.AiGenerationClient;
import de.melinadanhier.projectflow.generation.client.AiPreCheckTechnicalException;
import de.melinadanhier.projectflow.generation.dto.request.AiWizardSnapshot;
import de.melinadanhier.projectflow.generation.dto.response.AiPreCheckResult;
import de.melinadanhier.projectflow.generation.model.AiPreCheckErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class AiPreCheckProcessor {

    static final int MAX_AUTOMATIC_RETRIES = 2;

    private final AiGenerationClient aiGenerationClient;
    private final AiWorkflowStateService workflowStateService;
    private final AiPreCheckBackoff backoff;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void startAfterCommit(AiPreCheckRequestedEvent event) {
        AiWizardSnapshot snapshot;
        try {
            snapshot = workflowStateService.markRunningAndReadSnapshot(event.workflowId());
        } catch (RuntimeException exception) {
            finishWithTechnicalFailure(
                    event.workflowId(), AiPreCheckErrorCode.PRE_CHECK_INITIALIZATION_FAILED, exception);
            return;
        }
        try {
            executePreCheck(event, snapshot);
        } catch (RuntimeException exception) {
            finishWithTechnicalFailure(
                    event.workflowId(), AiPreCheckErrorCode.PRE_CHECK_PROCESSING_FAILED, exception);
        }
    }

    private void executePreCheck(AiPreCheckRequestedEvent event, AiWizardSnapshot snapshot) {
        int retries = 0;
        while (true) {
            try {
                AiPreCheckResult result = aiGenerationClient.preCheck(snapshot);
                workflowStateService.recordResult(event.workflowId(), result);
                return;
            } catch (AiPreCheckTechnicalException exception) {
                log.warn("Technischer KI-Pre-Check-Fehler für Workflow {} bei Versuch {} ({}).",
                        event.workflowId(), retries + 1, exception.getClass().getSimpleName());
                if (retries >= MAX_AUTOMATIC_RETRIES) {
                    finishWithTechnicalFailure(
                            event.workflowId(), AiPreCheckErrorCode.AI_PROVIDER_UNAVAILABLE, exception);
                    return;
                }
                retries = workflowStateService.recordAutomaticRetry(
                        event.workflowId(), AiPreCheckErrorCode.AI_PROVIDER_UNAVAILABLE);
                try {
                    backoff.waitBeforeRetry(retries);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    finishWithTechnicalFailure(
                            event.workflowId(), AiPreCheckErrorCode.RETRY_INTERRUPTED, interruptedException);
                    return;
                }
            } catch (RuntimeException exception) {
                finishWithTechnicalFailure(
                        event.workflowId(), AiPreCheckErrorCode.PRE_CHECK_PROCESSING_FAILED, exception);
                return;
            }
        }
    }

    private void finishWithTechnicalFailure(
            java.util.UUID workflowId,
            AiPreCheckErrorCode errorCode,
            Exception exception
    ) {
        log.error("KI-Pre-Check für Workflow {} wurde mit Fehlercode {} beendet.",
                workflowId, errorCode, exception);
        try {
            workflowStateService.recordTechnicalFailure(workflowId, errorCode);
        } catch (RuntimeException persistenceException) {
            log.error("Technischer Fehlerstatus für Workflow {} konnte nicht gespeichert werden.",
                    workflowId, persistenceException);
        }
    }
}
