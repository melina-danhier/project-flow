package de.melinadanhier.projectflow.generation.validation;

import de.melinadanhier.projectflow.generation.client.AiOutputValidationException;
import de.melinadanhier.projectflow.generation.dto.response.AiPreCheckResult;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PreCheckResultValidator {

    private final Validator validator;

    public void validate(AiPreCheckResult result) {
        if (result == null || !validator.validate(result).isEmpty()) {
            throw new AiOutputValidationException(
                    "Der KI-Pre-Check verletzt das erwartete Output-Schema.");
        }
    }
}
