package de.melinadanhier.projectflow.generation.service;

import de.melinadanhier.projectflow.generation.client.AiClient;
import de.melinadanhier.projectflow.generation.client.AiClientTechnicalException;
import de.melinadanhier.projectflow.generation.client.AiExecutionProperties;
import de.melinadanhier.projectflow.generation.client.AiOutputValidationException;
import de.melinadanhier.projectflow.generation.dto.request.AiGenerationRequest;
import de.melinadanhier.projectflow.generation.dto.request.AiWizardSnapshot;
import de.melinadanhier.projectflow.generation.dto.response.AiPreCheckProblem;
import de.melinadanhier.projectflow.generation.dto.response.AiPreCheckSeverity;
import de.melinadanhier.projectflow.generation.dto.response.GeneratedPlanResponse;
import de.melinadanhier.projectflow.generation.validation.GenerationResponseValidator;
import de.melinadanhier.projectflow.generation.validation.GenerationValidationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GenerationService {

    private final AiClient aiClient;
    private final GenerationResponseValidator responseValidator;
    private final AiExecutionProperties executionProperties;
    private final AiPreCheckBackoff backoff;

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
                if (validation.valid()) {
                    return response;
                }
                if (retries >= executionProperties.getMaxAutomaticRetries()) {
                    throw new AiOutputValidationException(
                            "Der generierte Plan verletzt auch nach den Output-Retries deterministische Bedingungen.");
                }
                retries++;
                request = new AiGenerationRequest(
                        confirmedSnapshot,
                        warnings,
                        validation.issues().stream().map(issue -> issue.message()).toList());
                waitBeforeRetry(retries);
            } catch (AiClientTechnicalException exception) {
                if (!exception.isRetryable() || retries >= executionProperties.getMaxAutomaticRetries()) {
                    throw exception;
                }
                retries++;
                waitBeforeRetry(retries);
            }
        }
    }

    private void waitBeforeRetry(int retryNumber) {
        try {
            backoff.waitBeforeRetry(retryNumber);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiClientTechnicalException("Der KI-Retry wurde unterbrochen.", exception, false);
        }
    }
}
