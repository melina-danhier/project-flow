package de.melinadanhier.projectflow.generation.service.coordination;

import de.melinadanhier.projectflow.generation.service.workflow.AiGenerationWorkflowService;
import de.melinadanhier.projectflow.generation.service.plan.AiPlanGenerationService;
import de.melinadanhier.projectflow.ai.exception.AiTechnicalError;
import de.melinadanhier.projectflow.ai.model.AiOperation;
import de.melinadanhier.projectflow.ai.model.generation.GeneratedPlanResponse;
import de.melinadanhier.projectflow.ai.exception.AiTechnicalException;
import de.melinadanhier.projectflow.ai.exception.AiOutputValidationException;
import de.melinadanhier.projectflow.generation.model.workflow.AiGenerationWork;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiPlanGenerationCoordinator {

    private final AiPlanGenerationService generationService;
    private final AiGenerationWorkflowService workflowService;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void generateClaimed(AiGenerationWork work) {
        UUID workflowId = work.workflowId();
        try {
            GeneratedPlanResponse result = generationService.generatePlan(
                    work.snapshot(), work.acknowledgedWarnings(), work.roundAttemptCount(),
                    work.promptVersion(), work.confirmedAssumptions(), work.rejectedAssumptions(),
                    () -> {
                        if (work.runId() == null) workflowService.recordProviderCall(workflowId);
                        else workflowService.recordProviderCall(workflowId, work.runId());
                    });
            if (work.runId() != null && !workflowService.isActive(workflowId, work.runId())) {
                return;
            }
            if (work.runId() == null) workflowService.recordSuccess(workflowId, result);
            else workflowService.recordSuccess(workflowId, work.runId(), result);
        } catch (AiOutputValidationException exception) {
            var error = classify(exception);
            log.warn("Plangenerierung für Workflow {} endete ohne valide Modellausgabe.",
                    workflowId, error.cause());
            if (work.runId() == null) workflowService.recordGenerationFailure(workflowId, error);
            else workflowService.recordGenerationFailure(workflowId, work.runId(), error);
        } catch (AiTechnicalException exception) {
            var error = classify(exception);
            log.warn("Plangenerierung für Workflow {} ist technisch fehlgeschlagen (Fehlercode {}).",
                    workflowId, error.errorCode(), error.cause());
            if (work.runId() == null) workflowService.recordTechnicalFailure(workflowId, error);
            else workflowService.recordTechnicalFailure(workflowId, work.runId(), error);
        } catch (RuntimeException exception) {
            log.error("Plangenerierung für Workflow {} ist technisch fehlgeschlagen (Fehlertyp {}).",
                    workflowId, exception.getClass().getSimpleName(), exception);
            if (work.runId() == null) workflowService.recordTechnicalFailure(workflowId, classify(exception));
            else workflowService.recordTechnicalFailure(workflowId, work.runId(), classify(exception));
        }
    }

    private AiTechnicalError classify(RuntimeException exception) {
        return AiTechnicalError.from(exception, AiOperation.PLAN_GENERATION);
    }
}
