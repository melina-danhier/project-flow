package de.melinadanhier.projectflow.ai.validation.precheck;

import de.melinadanhier.projectflow.ai.exception.AiOutputValidationException;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckResult;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PreCheckResultValidator {

    private final Validator validator;

    public void validate(AiPreCheckResult result) {
        if (result == null) {
            throw new AiOutputValidationException("Der KI-Pre-Check darf nicht null sein.");
        }
        List<String> issues = new ArrayList<>();
        validator.validate(result).stream()
                .sorted(Comparator.comparing(violation -> violation.getPropertyPath().toString()))
                .forEach(violation -> issues.add("BEAN_VALIDATION_FAILED | "
                        + violation.getPropertyPath() + " | " + violation.getMessage()));
        if (!issues.isEmpty()) {
            throw new AiOutputValidationException(
                    "Der KI-Pre-Check verletzt das erwartete Output-Schema.", issues);
        }
    }
}
