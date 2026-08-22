package de.melinadanhier.projectflow.ai.parser.precheck;

import de.melinadanhier.projectflow.ai.exception.AiOutputValidationException;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class PreCheckResponseParser {

    private final ObjectMapper objectMapper;

    public AiPreCheckResult parse(String json) {
        final AiPreCheckResult result;
        try {
            result = objectMapper.readerFor(AiPreCheckResult.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(json);
        } catch (RuntimeException exception) {
            throw new AiOutputValidationException(
                    "Der KI-Pre-Check ist kein gültiger JSON-Output.", exception);
        }
        if (result == null) {
            throw new AiOutputValidationException("Der KI-Pre-Check darf nicht null sein.");
        }
        return result;
    }
}
