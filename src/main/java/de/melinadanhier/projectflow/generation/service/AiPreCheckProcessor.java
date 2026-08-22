package de.melinadanhier.projectflow.generation.service;

import de.melinadanhier.projectflow.generation.client.AiClient;
import de.melinadanhier.projectflow.generation.client.AiClientTechnicalException;
import de.melinadanhier.projectflow.generation.client.AiExecutionProperties;
import de.melinadanhier.projectflow.generation.dto.request.AiWizardSnapshot;
import de.melinadanhier.projectflow.generation.dto.request.AiPreCheckRequest;
import de.melinadanhier.projectflow.generation.dto.response.AiPreCheckResult;
import de.melinadanhier.projectflow.generation.model.AiTechnicalErrorCode;
import de.melinadanhier.projectflow.generation.validation.PreCheckResultValidator;
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

    private final AiClient aiClient;
    private final AiWorkflowStateService workflowStateService;
    private final AiPreCheckBackoff backoff;
    private final AiExecutionProperties executionProperties;
    private final PreCheckResultValidator resultValidator;
    private final AiTechnicalErrorClassifier errorClassifier;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void startAfterCommit(AiPreCheckRequestedEvent event) {
        AiWizardSnapshot snapshot;
        try {
            var claimed = workflowStateService.claimPreCheckAndReadSnapshot(event.workflowId());
            if (claimed.isEmpty()) {
                return;
            }
            snapshot = claimed.get();
        } catch (RuntimeException exception) {
            finishWithTechnicalFailure(
                    event.workflowId(), AiTechnicalErrorCode.PRE_CHECK_INITIALIZATION_FAILED, exception);
            return;
        }
        try {
            executePreCheck(event, snapshot);
        } catch (RuntimeException exception) {
            finishWithTechnicalFailure(
                    event.workflowId(), AiTechnicalErrorCode.PRE_CHECK_PROCESSING_FAILED, exception);
        }
    }

    private void executePreCheck(AiPreCheckRequestedEvent event, AiWizardSnapshot snapshot) {
        AiPreCheckRequest request = new AiPreCheckRequest(snapshot);
        int retries = 0;
        while (true) {
            try {
                AiPreCheckResult result = aiClient.preCheck(request);
                resultValidator.validate(result);
                workflowStateService.recordResult(event.workflowId(), result);
                return;
            } catch (AiClientTechnicalException exception) {
                AiTechnicalErrorCode errorCode = errorClassifier.classify(exception);
                log.warn("Technischer KI-Pre-Check-Fehler für Workflow {} bei Versuch {} ({}).",
                        event.workflowId(), retries + 1, exception.getClass().getSimpleName());
                if (!exception.isRetryable()
                        || retries >= executionProperties.getMaxAutomaticRetries()) {
                    finishWithTechnicalFailure(
                            event.workflowId(), errorCode, exception);
                    return;
                }
                retries = workflowStateService.recordAutomaticRetry(
                        event.workflowId(), errorCode);
                try {
                    backoff.waitBeforeRetry(retries);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    finishWithTechnicalFailure(
                            event.workflowId(), AiTechnicalErrorCode.RETRY_INTERRUPTED, interruptedException);
                    return;
                }
            } catch (RuntimeException exception) {
                finishWithTechnicalFailure(
                        event.workflowId(), AiTechnicalErrorCode.PRE_CHECK_PROCESSING_FAILED, exception);
                return;
            }
        }
    }

    private void finishWithTechnicalFailure(
            java.util.UUID workflowId,
            AiTechnicalErrorCode errorCode,
            Exception exception
    ) {
        log.error("KI-Pre-Check für Workflow {} wurde mit Fehlercode {} beendet (Fehlertyp {}).",
                workflowId, errorCode, exception.getClass().getSimpleName());
        try {
            workflowStateService.recordTechnicalFailure(workflowId, errorCode);
        } catch (RuntimeException persistenceException) {
            log.error("Technischer Fehlerstatus für Workflow {} konnte nicht gespeichert werden (Fehlertyp {}).",
                    workflowId, persistenceException.getClass().getSimpleName());
        }
    }
}
