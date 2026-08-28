package de.melinadanhier.projectflow.generation.service.plan;

import de.melinadanhier.projectflow.ai.AiClient;
import de.melinadanhier.projectflow.ai.config.AiExecutionProperties;
import de.melinadanhier.projectflow.ai.exception.AiTechnicalException;
import de.melinadanhier.projectflow.ai.exception.AiOutputValidationException;
import de.melinadanhier.projectflow.ai.exception.AiTechnicalError;
import de.melinadanhier.projectflow.ai.exception.AiTechnicalErrorCode;
import de.melinadanhier.projectflow.ai.model.AiOperation;
import de.melinadanhier.projectflow.ai.model.generation.AiGenerationRequest;
import de.melinadanhier.projectflow.ai.model.generation.GeneratedPlanResponse;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckProblem;
import de.melinadanhier.projectflow.ai.validation.generation.GenerationResponseValidator;
import de.melinadanhier.projectflow.ai.validation.generation.GenerationValidationIssue;
import de.melinadanhier.projectflow.ai.validation.generation.GenerationValidationResult;
import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;
import de.melinadanhier.projectflow.generation.service.retry.AiRetryBackoff;
import de.melinadanhier.projectflow.ai.prompt.AiPromptVersions;
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
            List<AiPreCheckProblem> acknowledgedWarnings
    ) {
        return generatePlan(confirmedSnapshot, acknowledgedWarnings, 0,
                AiPromptVersions.GENERATION_PROMPT, () -> { });
    }

    public GeneratedPlanResponse generatePlan(
            AiWizardSnapshot confirmedSnapshot,
            List<AiPreCheckProblem> acknowledgedWarnings,
            int alreadyUsedAttempts,
            String promptVersion,
            Runnable beforeProviderCall
    ) {
        AiGenerationRequest request = new AiGenerationRequest(
                confirmedSnapshot, acknowledgedWarnings, List.of(), promptVersion);
        int attempts = alreadyUsedAttempts;
        int maxAttempts = executionProperties.getMaxAttempts();
        while (true) {
            if (attempts >= maxAttempts) {
                throw new AiTechnicalException(
                        AiTechnicalErrorCode.UNKNOWN_AI_ERROR,
                        "Das Versuchslimit dieser Generierungsrunde ist bereits ausgeschöpft.",
                        null);
            }
            try {
                beforeProviderCall.run();
                attempts++;
                GeneratedPlanResponse response = aiClient.generatePlan(request);
                GenerationValidationResult validation = responseValidator.validate(response, request);
                if (validation.isValid()) {
                    return response;
                }
                throw invalidResponse(validation);
            } catch (AiTechnicalException exception) {
                var error = AiTechnicalError.from(exception, AiOperation.PLAN_GENERATION);
                if (!error.isRetryable() || attempts >= maxAttempts) {
                    throw exception;
                }
                waitBeforeRetry(attempts);
            }
        }
    }

    private AiOutputValidationException invalidResponse(
            GenerationValidationResult validation
    ) {
        return new AiOutputValidationException(
                "Der generierte Plan verletzt deterministische Ausgabebedingungen.",
                validation.issues().stream().map(this::formatIssue).toList());
    }

    private String formatIssue(GenerationValidationIssue issue) {
        return issue.code() + " | " + issue.fieldPath() + " | " + issue.message();
    }

    private void waitBeforeRetry(int retryNumber) {
        try {
            retryBackoff.waitBeforeRetry(retryNumber);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiTechnicalException(
                    AiTechnicalErrorCode.RETRY_INTERRUPTED,
                    "Der KI-Retry wurde unterbrochen.", exception);
        }
    }
}
