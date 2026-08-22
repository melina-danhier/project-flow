package de.melinadanhier.projectflow.generation.service;

import de.melinadanhier.projectflow.common.exception.GenerationException;
import de.melinadanhier.projectflow.generation.dto.request.AiWizardSnapshot;
import de.melinadanhier.projectflow.generation.dto.response.AiPreCheckResult;
import de.melinadanhier.projectflow.generation.dto.response.GeneratedPlanResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class AiSnapshotCodec {

    private final ObjectMapper objectMapper;

    public String writeSnapshot(AiWizardSnapshot snapshot) {
        return write(snapshot, "Der bestätigte Wizard-Stand konnte nicht serialisiert werden.");
    }

    public AiWizardSnapshot readSnapshot(String json) {
        try {
            String normalized = json != null && json.startsWith("\"")
                    ? objectMapper.readValue(json, String.class)
                    : json;
            return objectMapper.readValue(normalized, AiWizardSnapshot.class);
        } catch (JacksonException exception) {
            throw new GenerationException("Der bestätigte Wizard-Stand konnte nicht gelesen werden.", exception);
        }
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
        try {
            String normalized = json != null && json.startsWith("\"")
                    ? objectMapper.readValue(json, String.class)
                    : json;
            return objectMapper.readValue(normalized, type);
        } catch (JacksonException exception) {
            throw new GenerationException(errorMessage, exception);
        }
    }

    private String write(Object value, String errorMessage) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new GenerationException(errorMessage, exception);
        }
    }
}
