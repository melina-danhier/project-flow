package de.melinadanhier.projectflow.generation.service.precheck;

import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;
import de.melinadanhier.projectflow.generation.service.workflow.AiPreCheckWorkflowService;
import de.melinadanhier.projectflow.ai.provider.AiClient;
import de.melinadanhier.projectflow.ai.config.AiExecutionProperties;
import de.melinadanhier.projectflow.ai.exception.AiTechnicalErrorCode;
import de.melinadanhier.projectflow.ai.exception.AiTechnicalException;
import de.melinadanhier.projectflow.ai.exception.AiTechnicalError;
import de.melinadanhier.projectflow.ai.model.AiOperation;
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
            finishWithTechnicalFailure(event.workflowId(), classify(exception));
            return;
        }
        try {
            executePreCheck(event, snapshot);
        } catch (RuntimeException exception) {
            finishWithTechnicalFailure(event.workflowId(), classify(exception));
        }
    }

    private void executePreCheck(AiPreCheckRequestedEvent event, AiWizardSnapshot snapshot) {
        AiPreCheckRequest request = new AiPreCheckRequest(snapshot);
        int completedRetries = workflowService.getPreCheckRetryCount(event.workflowId());
        while (true) {
            try {
                AiPreCheckResult result = aiClient.preCheck(request);
                resultValidator.validate(result);
                workflowService.recordResult(event.workflowId(), result);
                return;
            } catch (AiTechnicalException exception) {
                AiTechnicalError error = classify(exception);
                int attemptNumber = completedRetries + 1;
                log.warn("Technischer KI-Pre-Check-Fehler workflowId={} attempt={} schemaVersion={} errorCode={}.",
                        event.workflowId(), attemptNumber,
                        de.melinadanhier.projectflow.ai.model.AiSchemaVersions.PRE_CHECK, error.errorCode());
                if (!error.isRetryable()
                        || attemptNumber >= executionProperties.getMaxAttempts()) {
                    finishWithTechnicalFailure(event.workflowId(), error);
                    return;
                }
                var recordedRetry = workflowService.recordRetry(event.workflowId(), error);
                if (recordedRetry.isEmpty()) {
                    return;
                }
                completedRetries = recordedRetry.getAsInt();
                try {
                    backoff.waitBeforeRetry(completedRetries);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    finishWithTechnicalFailure(event.workflowId(), classify(
                            new AiTechnicalException(
                                    AiTechnicalErrorCode.RETRY_INTERRUPTED,
                                    "Der KI-Pre-Check-Retry wurde unterbrochen.", interruptedException)));
                    return;
                }
                if (workflowService.claimAndReadSnapshot(event.workflowId()).isEmpty()) {
                    return;
                }
            } catch (RuntimeException exception) {
                finishWithTechnicalFailure(event.workflowId(), classify(exception));
                return;
            }
        }
    }

    private AiTechnicalError classify(RuntimeException exception) {
        return AiTechnicalError.from(exception, AiOperation.PRE_CHECK);
    }

    private void finishWithTechnicalFailure(java.util.UUID workflowId, AiTechnicalError error) {
        log.error("KI-Pre-Check beendet workflowId={} schemaVersion={} errorCode={}.",
                workflowId, de.melinadanhier.projectflow.ai.model.AiSchemaVersions.PRE_CHECK,
                error.errorCode(), error.cause());
        try {
            workflowService.recordFailure(workflowId, error);
        } catch (RuntimeException persistenceException) {
            log.error("Technischer KI-Fehlerstatus konnte nicht gespeichert werden workflowId={} errorCode={}.",
                    workflowId, error.errorCode(), persistenceException);
        }
    }
}
