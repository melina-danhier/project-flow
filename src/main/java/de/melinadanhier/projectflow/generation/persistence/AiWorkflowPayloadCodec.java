package de.melinadanhier.projectflow.generation.persistence;

import de.melinadanhier.projectflow.ai.model.generation.GeneratedPlanResponse;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckResult;
import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;
import de.melinadanhier.projectflow.common.exception.GenerationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class AiWorkflowPayloadCodec {

    private final ObjectMapper objectMapper;

    public String writeSnapshot(AiWizardSnapshot snapshot) {
        return write(snapshot, "Der bestätigte Wizard-Stand konnte nicht serialisiert werden.");
    }

    public AiWizardSnapshot readSnapshot(String json) {
        return read(json, AiWizardSnapshot.class,
                "Der bestätigte Wizard-Stand konnte nicht gelesen werden.");
    }

    public String writePreCheckResult(AiPreCheckResult result) {
        return write(result, "Das Ergebnis der KI-Prüfung konnte nicht serialisiert werden.");
    }

    public AiPreCheckResult readPreCheckResult(String json) {
        return read(json, AiPreCheckResult.class,
                "Das Ergebnis der KI-Prüfung konnte nicht gelesen werden.");
    }

    public String writeGeneratedPlan(GeneratedPlanResponse result) {
        return write(result, "Der generierte Plan konnte nicht serialisiert werden.");
    }

    private <T> T read(String json, Class<T> type, String errorMessage) {
        if (json == null || json.isBlank()) {
            throw new GenerationException(errorMessage + " Die gespeicherte Payload ist leer.");
        }
        try {
            String normalized = normalizeLegacyJsonString(json);
            return objectMapper.readValue(normalized, type);
        } catch (JacksonException exception) {
            throw new GenerationException(errorMessage, exception);
        }
    }

    private String write(Object value, String errorMessage) {
        if (value == null) {
            throw new GenerationException(errorMessage + " Die Payload darf nicht null sein.");
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new GenerationException(errorMessage, exception);
        }
    }

    /** Unterstützt Payloads, die vor der direkten JSONB-Speicherung als JSON-String abgelegt wurden. */
    private String normalizeLegacyJsonString(String json) throws JacksonException {
        return json.startsWith("\"") ? objectMapper.readValue(json, String.class) : json;
    }
}
