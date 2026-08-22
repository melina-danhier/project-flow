package de.melinadanhier.projectflow.generation.service;

import de.melinadanhier.projectflow.generation.dto.request.AiWizardSnapshot;
import de.melinadanhier.projectflow.generation.dto.response.AiPreCheckProblem;
import de.melinadanhier.projectflow.generation.dto.response.GeneratedPlanResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiPlanGenerationCoordinator {

    private final GenerationService generationService;
    private final AiWorkflowStateService workflowStateService;
    private final AiTechnicalErrorClassifier errorClassifier;

    public void generateClaimed(AiGenerationWork work) {
        UUID workflowId = work.workflowId();
        try {
            GeneratedPlanResponse result = generationService.generatePlan(work.snapshot(), work.ignoredWarnings());
            workflowStateService.recordGeneratedPlan(workflowId, result);
        } catch (RuntimeException exception) {
            log.error("Plangenerierung für Workflow {} ist technisch fehlgeschlagen (Fehlertyp {}).",
                    workflowId, exception.getClass().getSimpleName());
            workflowStateService.recordGenerationFailure(workflowId, errorClassifier.classify(exception));
        }
    }
}
