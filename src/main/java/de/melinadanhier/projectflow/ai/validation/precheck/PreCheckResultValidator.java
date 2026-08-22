package de.melinadanhier.projectflow.ai.validation.precheck;

import de.melinadanhier.projectflow.ai.exception.AiOutputValidationException;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckResult;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PreCheckResultValidator {

    private final Validator validator;

    public void validate(AiPreCheckResult result) {
        if (result == null) {
            throw new AiOutputValidationException("Der KI-Pre-Check darf nicht null sein.");
        }
        if (!validator.validate(result).isEmpty()) {
            throw new AiOutputValidationException(
                    "Der KI-Pre-Check verletzt das erwartete Output-Schema.");
        }
    }
}
