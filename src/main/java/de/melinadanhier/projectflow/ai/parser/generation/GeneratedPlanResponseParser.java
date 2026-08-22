package de.melinadanhier.projectflow.ai.parser.generation;

import de.melinadanhier.projectflow.ai.exception.AiOutputValidationException;
import de.melinadanhier.projectflow.ai.model.generation.GeneratedPlanResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class GeneratedPlanResponseParser {

    private final ObjectMapper objectMapper;

    public GeneratedPlanResponse parse(String json) {
        final GeneratedPlanResponse result;
        try {
            result = objectMapper.readerFor(GeneratedPlanResponse.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
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
}
