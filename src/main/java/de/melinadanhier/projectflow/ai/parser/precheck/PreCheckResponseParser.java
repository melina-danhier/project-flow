package de.melinadanhier.projectflow.ai.parser.precheck;

import de.melinadanhier.projectflow.ai.exception.AiOutputValidationException;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

import static de.melinadanhier.projectflow.ai.validation.AiResponseLimits.MAX_RESPONSE_BYTES;

@Component
@RequiredArgsConstructor
public class PreCheckResponseParser {

    private final ObjectMapper objectMapper;

    public AiPreCheckResult parse(String json) {
        requireAcceptableSize(json);
        final AiPreCheckResult result;
        try {
            result = objectMapper.readerFor(AiPreCheckResult.class)
                    .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
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

    private void requireAcceptableSize(String json) {
        if (json == null || json.getBytes(StandardCharsets.UTF_8).length > MAX_RESPONSE_BYTES) {
            throw new AiOutputValidationException("Der KI-Pre-Check fehlt oder überschreitet die zulässige Größe.");
        }
    }
}
