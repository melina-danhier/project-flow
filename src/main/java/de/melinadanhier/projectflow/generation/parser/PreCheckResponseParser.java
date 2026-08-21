package de.melinadanhier.projectflow.generation.parser;

import de.melinadanhier.projectflow.generation.client.AiOutputValidationException;
import de.melinadanhier.projectflow.generation.dto.response.AiPreCheckResult;
import de.melinadanhier.projectflow.generation.prompt.AiSchemaVersions;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class PreCheckResponseParser {

    private final ObjectMapper objectMapper;
    private final Validator validator;

    public AiPreCheckResult parse(String json) {
        final AiPreCheckResult result;
        try {
            result = objectMapper.readerFor(AiPreCheckResult.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(json);
        } catch (RuntimeException exception) {
            throw new AiOutputValidationException("Der KI-Pre-Check ist kein gültiger JSON-Output.", exception);
        }
        validate(result);
        if (!AiSchemaVersions.PRE_CHECK.equals(result.schemaVersion())) {
            throw new AiOutputValidationException(
                    "Nicht unterstützte Pre-Check-Schemaversion: " + result.schemaVersion());
        }
        return result;
    }

    private void validate(AiPreCheckResult result) {
        Set<ConstraintViolation<AiPreCheckResult>> violations = validator.validate(result);
        if (!violations.isEmpty()) {
            throw new AiOutputValidationException("Der KI-Pre-Check verletzt das erwartete Output-Schema.");
        }
    }
}
