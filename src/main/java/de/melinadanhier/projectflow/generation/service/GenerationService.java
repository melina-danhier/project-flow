package de.melinadanhier.projectflow.generation.service;

import de.melinadanhier.projectflow.generation.client.AiClient;
import de.melinadanhier.projectflow.generation.dto.request.AiGenerationRequest;
import de.melinadanhier.projectflow.generation.dto.request.AiWizardSnapshot;
import de.melinadanhier.projectflow.generation.dto.response.AiPreCheckProblem;
import de.melinadanhier.projectflow.generation.dto.response.AiPreCheckSeverity;
import de.melinadanhier.projectflow.generation.dto.response.GeneratedPlanResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GenerationService {

    private final AiClient aiClient;

    public GeneratedPlanResponse generatePlan(
            AiWizardSnapshot confirmedSnapshot,
            List<AiPreCheckProblem> explicitlyIgnoredWarnings
    ) {
        List<AiPreCheckProblem> warnings = explicitlyIgnoredWarnings == null ? List.of()
                : explicitlyIgnoredWarnings.stream()
                        .filter(problem -> problem.severity() == AiPreCheckSeverity.WARNING)
                        .toList();
        return aiClient.generatePlan(new AiGenerationRequest(confirmedSnapshot, warnings));
    }
}
