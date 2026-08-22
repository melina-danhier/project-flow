package de.melinadanhier.projectflow.generation.service.coordination;

import de.melinadanhier.projectflow.generation.service.workflow.AiGenerationWorkflowService;
import de.melinadanhier.projectflow.generation.service.plan.AiPlanGenerationService;
import de.melinadanhier.projectflow.ai.exception.AiTechnicalErrorClassifier;
import de.melinadanhier.projectflow.ai.model.generation.GeneratedPlanResponse;
import de.melinadanhier.projectflow.generation.model.workflow.AiGenerationWork;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiPlanGenerationCoordinator {

    private final AiPlanGenerationService generationService;
    private final AiGenerationWorkflowService workflowService;
    private final AiTechnicalErrorClassifier errorClassifier;

    public void generateClaimed(AiGenerationWork work) {
        UUID workflowId = work.workflowId();
        try {
            GeneratedPlanResponse result = generationService.generatePlan(work.snapshot(), work.ignoredWarnings());
            workflowService.recordSuccess(workflowId, result);
        } catch (RuntimeException exception) {
            log.error("Plangenerierung für Workflow {} ist technisch fehlgeschlagen (Fehlertyp {}).",
                    workflowId, exception.getClass().getSimpleName());
            workflowService.recordFailure(workflowId, errorClassifier.classify(exception));
        }
    }
}
