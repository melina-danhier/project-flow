package de.melinadanhier.projectflow.ai.prompt;

import de.melinadanhier.projectflow.ai.exception.AiTechnicalErrorCode;
import de.melinadanhier.projectflow.ai.exception.AiTechnicalException;
import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckRequest;
import de.melinadanhier.projectflow.common.exception.GenerationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class PreCheckPromptBuilder {

    private static final String SYSTEM_INSTRUCTIONS_TEMPLATE = """
            Du prüfst ausschließlich die nachfolgend getrennt übergebenen, vom Nutzer bestätigten
            Wizard-Daten auf fachliche Plausibilität für eine sinnvolle Projektplanung.

            Regeln:
            - Erzeuge noch keinen Projektplan und verändere oder korrigiere keine Nutzereingabe.
            - Melde nur planungsrelevante fachliche Probleme, keine technischen Validierungsfehler.
            - Melde insbesondere keine fehlenden Pflichtfelder, ungültigen Wertebereiche oder eine
              deterministisch erkennbare falsche Datumsreihenfolge; diese werden serverseitig geprüft.
            - Verwende ausschließlich WARNING oder ERROR. WARNING ist akzeptierbar, wenn eine Planung
              trotz eines unrealistischen, riskanten oder problematischen Aspekts sinnvoll möglich ist.
              ERROR ist nur zulässig, wenn eine sinnvolle Generierung fachlich nicht oder kaum möglich ist.
            - Formuliere message verständlich und suggestion als konkrete Änderungsempfehlung.
            - Erfinde keine fehlenden Nutzerinformationen.
            - Gib bei keinen Problemen eine leere problems-Liste zurück.
            """;

    private final ObjectMapper objectMapper;

    public AiPrompt build(AiWizardSnapshot confirmedSnapshot) {
        return build(new AiPreCheckRequest(confirmedSnapshot));
    }

    public AiPrompt build(AiPreCheckRequest request) {
        return new AiPrompt(
                AiPromptVersions.PRE_CHECK_PROMPT,
                SYSTEM_INSTRUCTIONS_TEMPLATE,
                serializeRequestData(request)
        );
    }

    private String serializeRequestData(AiPreCheckRequest request) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("confirmedWizardData", request.confirmedWizardData());
        if (!request.previousValidationIssues().isEmpty()) {
            context.put("previousOutputValidationIssues", request.previousValidationIssues());
        }
        try {
            return objectMapper.writeValueAsString(context);
        } catch (JacksonException exception) {
            throw new GenerationException(
                    "Die bestätigten Wizard-Daten konnten nicht für den Pre-Check aufbereitet werden.",
                    exception
            );
        }
    }
}
