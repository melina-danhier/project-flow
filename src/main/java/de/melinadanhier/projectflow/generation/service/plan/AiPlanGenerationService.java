package de.melinadanhier.projectflow.generation.service.plan;

import de.melinadanhier.projectflow.ai.AiClient;
import de.melinadanhier.projectflow.ai.config.AiExecutionProperties;
import de.melinadanhier.projectflow.ai.exception.AiClientTechnicalException;
import de.melinadanhier.projectflow.ai.exception.AiOutputValidationException;
import de.melinadanhier.projectflow.ai.exception.AiTechnicalErrorCode;
import de.melinadanhier.projectflow.ai.model.generation.AiGenerationRequest;
import de.melinadanhier.projectflow.ai.model.generation.GeneratedPlanResponse;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckProblem;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckSeverity;
import de.melinadanhier.projectflow.ai.validation.generation.GenerationResponseValidator;
import de.melinadanhier.projectflow.ai.validation.generation.GenerationValidationIssue;
import de.melinadanhier.projectflow.ai.validation.generation.GenerationValidationResult;
import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;
import de.melinadanhier.projectflow.generation.service.retry.AiRetryBackoff;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AiPlanGenerationService {

    private final AiClient aiClient;
    private final GenerationResponseValidator responseValidator;
    private final AiExecutionProperties executionProperties;
    private final AiRetryBackoff retryBackoff;

    public GeneratedPlanResponse generatePlan(
            AiWizardSnapshot confirmedSnapshot,
            List<AiPreCheckProblem> explicitlyIgnoredWarnings
    ) {
        List<AiPreCheckProblem> warnings = explicitlyIgnoredWarnings == null ? List.of()
                : explicitlyIgnoredWarnings.stream()
                        .filter(problem -> problem.severity() == AiPreCheckSeverity.WARNING)
                        .toList();
        AiGenerationRequest request = new AiGenerationRequest(confirmedSnapshot, warnings);
        int retries = 0;
        while (true) {
            try {
                GeneratedPlanResponse response = aiClient.generatePlan(request);
                GenerationValidationResult validation = responseValidator.validate(response, request);
                if (validation.isValid()) {
                    return response;
                }
                if (retries >= executionProperties.getMaxAutomaticRetries()) {
                    throw exhaustedValidationRetries(validation);
                }
                retries++;
                request = new AiGenerationRequest(
                        confirmedSnapshot,
                        warnings,
                        validation.issues().stream().map(this::formatIssue).toList());
                waitBeforeRetry(retries);
            } catch (AiClientTechnicalException exception) {
                if (!exception.isRetryable()
                        || retries >= executionProperties.getMaxAutomaticRetries()) {
                    throw exception;
                }
                retries++;
                waitBeforeRetry(retries);
            }
        }
    }

    private AiOutputValidationException exhaustedValidationRetries(
            GenerationValidationResult validation
    ) {
        return new AiOutputValidationException(
                "Der generierte Plan verletzt auch nach den Output-Retries deterministische Bedingungen: "
                        + validation.issues().stream().map(this::formatIssue).toList());
    }

    private String formatIssue(GenerationValidationIssue issue) {
        return issue.code() + ": " + issue.message();
    }

    private void waitBeforeRetry(int retryNumber) {
        try {
            retryBackoff.waitBeforeRetry(retryNumber);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiClientTechnicalException(
                    AiTechnicalErrorCode.RETRY_INTERRUPTED,
                    "Der KI-Retry wurde unterbrochen.",
                    exception,
                    false);
        }
    }
}
