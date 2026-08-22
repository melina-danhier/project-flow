package de.melinadanhier.projectflow.generation.validation;

import de.melinadanhier.projectflow.generation.client.AiOutputValidationException;
import de.melinadanhier.projectflow.generation.dto.request.AiGenerationRequest;
import de.melinadanhier.projectflow.generation.dto.request.AiWizardSnapshot;
import de.melinadanhier.projectflow.generation.dto.response.GeneratedPlanResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
@RequiredArgsConstructor
public class GeneratedPlanValidator {

    private final GenerationResponseValidator responseValidator;

    public void validate(GeneratedPlanResponse response) {
        AiGenerationRequest request = new AiGenerationRequest(
                new AiWizardSnapshot(null, null, null, null, null, null, null, null, null, null),
                List.of());
        GenerationValidationResult result = responseValidator.validate(response, request);
        if (!result.valid()) {
            throw new AiOutputValidationException(
                    "Der generierte Plan verletzt deterministische Bedingungen: "
                            + result.issues().stream().map(GenerationValidationIssue::message).toList());
        }
    }
}
