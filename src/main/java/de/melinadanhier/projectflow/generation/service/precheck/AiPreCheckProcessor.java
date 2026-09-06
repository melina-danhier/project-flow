package de.melinadanhier.projectflow.generation.service.precheck;

import de.melinadanhier.projectflow.ai.config.AiExecutionProperties;
import de.melinadanhier.projectflow.ai.exception.AiTechnicalError;
import de.melinadanhier.projectflow.ai.exception.AiTechnicalErrorCode;
import de.melinadanhier.projectflow.ai.exception.AiTechnicalException;
import de.melinadanhier.projectflow.ai.model.AiOperation;
import de.melinadanhier.projectflow.ai.model.AiSchemaVersions;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckRequest;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckResult;
import de.melinadanhier.projectflow.ai.prompt.AiPromptVersions;
import de.melinadanhier.projectflow.ai.provider.AiClient;
import de.melinadanhier.projectflow.ai.validation.precheck.PreCheckResultValidator;
import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;
import de.melinadanhier.projectflow.generation.service.retry.AiRetryBackoff;
import de.melinadanhier.projectflow.generation.service.workflow.AiPreCheckWorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class AiPreCheckProcessor {

    private final AiClient aiClient;
    private final AiPreCheckWorkflowService workflowService;
    private final AiRetryBackoff backoff;
    private final AiExecutionProperties executionProperties;
    private final PreCheckResultValidator resultValidator;

    public void processClaimed(UUID workflowId, UUID runId, AiWizardSnapshot snapshot) {
        try {
            executePreCheck(workflowId, runId, snapshot);
        } catch (RuntimeException exception) {
            finishWithTechnicalFailure(workflowId, runId, classify(exception));
        }
    }

    private void executePreCheck(UUID workflowId, UUID runId, AiWizardSnapshot snapshot) {
        AiPreCheckRequest request = new AiPreCheckRequest(snapshot);
        int completedRetries = workflowService.getPreCheckRetryCount(workflowId);
        while (true) {
            try {
                workflowService.recordProviderCall(workflowId, runId,
                        AiPromptVersions.PRE_CHECK_PROMPT, AiSchemaVersions.PRE_CHECK);
                AiPreCheckResult result = aiClient.preCheck(request);
                if (!workflowService.isActive(workflowId, runId)) {
                    return;
                }
                resultValidator.validate(result);
                workflowService.recordResult(workflowId, runId, result);
                return;
            } catch (AiTechnicalException exception) {
                AiTechnicalError error = classify(exception);
                int attemptNumber = completedRetries + 1;
                log.warn("Technischer KI-Pre-Check-Fehler workflowId={} attempt={} schemaVersion={} errorCode={}.",
                        workflowId, attemptNumber,
                        AiSchemaVersions.PRE_CHECK, error.errorCode());
                if (!error.isRetryable()
                        || attemptNumber >= executionProperties.getMaxAttempts()) {
                    finishWithTechnicalFailure(workflowId, runId, error);
                    return;
                }
                var recordedRetry = workflowService.recordRetry(workflowId, runId, error);
                if (recordedRetry.isEmpty()) {
                    return;
                }
                completedRetries = recordedRetry.getAsInt();
                try {
                    backoff.waitBeforeRetry(completedRetries);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    finishWithTechnicalFailure(workflowId, runId, classify(
                            new AiTechnicalException(
                                    AiTechnicalErrorCode.RETRY_INTERRUPTED,
                                    "Der KI-Pre-Check-Retry wurde unterbrochen.", interruptedException)));
                    return;
                }
                var reclaimed = workflowService.claimAndReadSnapshot(workflowId, runId);
                if (reclaimed.isEmpty()) {
                    return;
                }
            } catch (RuntimeException exception) {
                finishWithTechnicalFailure(workflowId, runId, classify(exception));
                return;
            }
        }
    }

    private AiTechnicalError classify(RuntimeException exception) {
        return AiTechnicalError.from(exception, AiOperation.PRE_CHECK);
    }

    private void finishWithTechnicalFailure(UUID workflowId, UUID runId,
                                            AiTechnicalError error) {
        log.error("KI-Pre-Check beendet workflowId={} schemaVersion={} errorCode={}.",
                workflowId, AiSchemaVersions.PRE_CHECK,
                error.errorCode(), error.cause());
        try {
            workflowService.recordFailure(workflowId, runId, error);
        } catch (RuntimeException persistenceException) {
            log.error("Technischer KI-Fehlerstatus konnte nicht gespeichert werden workflowId={} errorCode={}.",
                    workflowId, error.errorCode(), persistenceException);
        }
    }
}
