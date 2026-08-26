package de.melinadanhier.projectflow.generation.service.precheck;

import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;
import de.melinadanhier.projectflow.generation.service.workflow.AiPreCheckWorkflowService;
import de.melinadanhier.projectflow.ai.AiClient;
import de.melinadanhier.projectflow.ai.config.AiExecutionProperties;
import de.melinadanhier.projectflow.ai.exception.AiClientTechnicalException;
import de.melinadanhier.projectflow.ai.exception.AiTechnicalErrorClassifier;
import de.melinadanhier.projectflow.ai.exception.AiTechnicalErrorCode;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckRequest;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckResult;
import de.melinadanhier.projectflow.ai.validation.precheck.PreCheckResultValidator;
import de.melinadanhier.projectflow.generation.event.AiPreCheckRequestedEvent;
import de.melinadanhier.projectflow.generation.service.retry.AiRetryBackoff;
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
    private final AiPreCheckWorkflowService workflowService;
    private final AiRetryBackoff backoff;
    private final AiExecutionProperties executionProperties;
    private final PreCheckResultValidator resultValidator;
    private final AiTechnicalErrorClassifier errorClassifier;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void startAfterCommit(AiPreCheckRequestedEvent event) {
        AiWizardSnapshot snapshot;
        try {
            var claimed = workflowService.claimAndReadSnapshot(event.workflowId());
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
        int retries = workflowService.getPreCheckRetryCount(event.workflowId());
        while (true) {
            try {
                AiPreCheckResult result = aiClient.preCheck(request);
                resultValidator.validate(result);
                workflowService.recordResult(event.workflowId(), result);
                return;
            } catch (AiClientTechnicalException exception) {
                AiTechnicalErrorCode errorCode = errorClassifier.classify(exception);
                log.warn("Technischer KI-Pre-Check-Fehler workflowId={} attempt={} schemaVersion={} errorCode={}.",
                        event.workflowId(), retries + 1,
                        de.melinadanhier.projectflow.ai.model.AiSchemaVersions.PRE_CHECK, errorCode);
                if (!exception.isRetryable()
                        || retries >= executionProperties.getMaxAutomaticRetries()) {
                    finishWithTechnicalFailure(
                            event.workflowId(), errorCode, exception);
                    return;
                }
                var recordedRetry = workflowService.recordRetry(event.workflowId(), errorCode);
                if (recordedRetry.isEmpty()) {
                    return;
                }
                retries = recordedRetry.getAsInt();
                if (exception instanceof de.melinadanhier.projectflow.ai.exception.AiOutputValidationException
                        validationException) {
                    request = new AiPreCheckRequest(snapshot, validationException.getValidationIssues());
                }
                try {
                    backoff.waitBeforeRetry(retries);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    finishWithTechnicalFailure(
                            event.workflowId(), AiTechnicalErrorCode.RETRY_INTERRUPTED, interruptedException);
                    return;
                }
                if (workflowService.claimAndReadSnapshot(event.workflowId()).isEmpty()) {
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
        log.error("KI-Pre-Check beendet workflowId={} schemaVersion={} errorCode={}.",
                workflowId, de.melinadanhier.projectflow.ai.model.AiSchemaVersions.PRE_CHECK, errorCode);
        try {
            workflowService.recordFailure(workflowId, errorCode);
        } catch (RuntimeException persistenceException) {
            log.error("Technischer KI-Fehlerstatus konnte nicht gespeichert werden workflowId={} errorCode={}.",
                    workflowId, errorCode);
        }
    }
}
