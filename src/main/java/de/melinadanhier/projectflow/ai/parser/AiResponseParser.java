package de.melinadanhier.projectflow.ai.parser;

import de.melinadanhier.projectflow.ai.exception.AiOutputValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

import static de.melinadanhier.projectflow.ai.validation.AiResponseLimits.MAX_RESPONSE_BYTES;

/** Deserialisiert ausschließlich rohe Provider-Ausgaben, nicht bereits typisierte SDK-Ergebnisse. */
@Component
@RequiredArgsConstructor
public class AiResponseParser {
    private final ObjectMapper objectMapper;

    public <T> T parse(String json, Class<T> responseType) {
        if (json == null || json.isBlank() || json.length() > MAX_RESPONSE_BYTES
                || json.getBytes(StandardCharsets.UTF_8).length > MAX_RESPONSE_BYTES) {
            throw new AiOutputValidationException("Die KI-Antwort fehlt oder überschreitet die zulässige Größe.");
        }
        final T result;
        try {
            result = objectMapper.readerFor(responseType)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readValue(json);
        } catch (JacksonException exception) {
            throw new AiOutputValidationException("Die KI-Antwort ist kein gültiger strukturierter JSON-Output.", exception);
        }
        if (result == null) {
            throw new AiOutputValidationException("Die KI-Antwort darf nicht null sein.");
        }
        return result;
    }
}
