package de.melinadanhier.projectflow.ai.parser.generation;

import de.melinadanhier.projectflow.ai.exception.AiOutputValidationException;
import de.melinadanhier.projectflow.ai.model.generation.GeneratedPlanResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

import static de.melinadanhier.projectflow.ai.validation.AiResponseLimits.MAX_RESPONSE_BYTES;

@Component
@RequiredArgsConstructor
public class GeneratedPlanResponseParser {

    private final ObjectMapper objectMapper;

    public GeneratedPlanResponse parse(String json) {
        requireAcceptableSize(json);
        final GeneratedPlanResponse result;
        try {
            result = objectMapper.readerFor(GeneratedPlanResponse.class)
                    .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(json);
        } catch (RuntimeException exception) {
            throw new AiOutputValidationException(
                    "Der generierte Plan ist kein gültiger JSON-Output.", exception);
        }
        if (result == null) {
            throw new AiOutputValidationException("Der generierte Plan darf nicht null sein.");
        }
        return result;
    }

    private void requireAcceptableSize(String json) {
        if (json == null || json.getBytes(StandardCharsets.UTF_8).length > MAX_RESPONSE_BYTES) {
            throw new AiOutputValidationException("Der generierte Plan fehlt oder überschreitet die zulässige Größe.");
        }
    }
}
