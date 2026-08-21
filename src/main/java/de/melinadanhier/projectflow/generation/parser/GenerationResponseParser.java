package de.melinadanhier.projectflow.generation.parser;

import de.melinadanhier.projectflow.generation.client.AiOutputValidationException;
import de.melinadanhier.projectflow.generation.dto.response.GeneratedPlanResponse;
import de.melinadanhier.projectflow.generation.prompt.AiSchemaVersions;
import de.melinadanhier.projectflow.generation.validation.GeneratedPlanValidator;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class GenerationResponseParser {

    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final GeneratedPlanValidator generatedPlanValidator;

    public GeneratedPlanResponse parse(String json) {
        final GeneratedPlanResponse result;
        try {
            result = objectMapper.readerFor(GeneratedPlanResponse.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(json);
        } catch (RuntimeException exception) {
            throw new AiOutputValidationException("Der generierte Plan ist kein gültiger JSON-Output.", exception);
        }
        Set<ConstraintViolation<GeneratedPlanResponse>> violations = validator.validate(result);
        if (!violations.isEmpty()) {
            throw new AiOutputValidationException("Der generierte Plan verletzt das erwartete Output-Schema.");
        }
        if (!AiSchemaVersions.GENERATION.equals(result.schemaVersion())) {
            throw new AiOutputValidationException(
                    "Nicht unterstützte Generierungs-Schemaversion: " + result.schemaVersion());
        }
        generatedPlanValidator.validate(result);
        return result;
    }
}
