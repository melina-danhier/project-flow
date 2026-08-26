package de.melinadanhier.projectflow.generation.service.coordination;

import de.melinadanhier.projectflow.generation.service.workflow.AiGenerationWorkflowService;
import de.melinadanhier.projectflow.generation.service.plan.AiPlanGenerationService;
import de.melinadanhier.projectflow.ai.exception.AiTechnicalErrorClassifier;
import de.melinadanhier.projectflow.ai.model.generation.GeneratedPlanResponse;
import de.melinadanhier.projectflow.ai.exception.AiClientTechnicalException;
import de.melinadanhier.projectflow.ai.exception.AiOutputValidationException;
import de.melinadanhier.projectflow.ai.exception.AiIncompleteResponseException;
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
            GeneratedPlanResponse result = generationService.generatePlan(
                    work.snapshot(), work.acknowledgedWarnings(), work.roundAttemptCount(),
                    work.promptVersion(),
                    () -> workflowService.recordProviderCall(workflowId));
            workflowService.recordSuccess(workflowId, result);
        } catch (AiIncompleteResponseException exception) {
            var errorCode = errorClassifier.classify(exception);
            log.warn("Plangenerierung für Workflow {} endete mit unvollständiger Providerantwort.", workflowId);
            workflowService.recordTechnicalFailure(
                    workflowId, errorCode, exception.isRetryable(),
                    errorClassifier.diagnosis(errorCode));
        } catch (AiOutputValidationException exception) {
            log.warn("Plangenerierung für Workflow {} endete ohne valide Modellausgabe.", workflowId);
            workflowService.recordGenerationFailure(
                    workflowId,
                    errorClassifier.diagnosis(errorClassifier.classify(exception)));
        } catch (AiClientTechnicalException exception) {
            var errorCode = errorClassifier.classify(exception);
            log.warn("Plangenerierung für Workflow {} ist technisch fehlgeschlagen (Fehlercode {}).",
                    workflowId, errorCode);
            workflowService.recordTechnicalFailure(
                    workflowId, errorCode, exception.isRetryable(),
                    errorClassifier.diagnosis(errorCode));
        } catch (RuntimeException exception) {
            log.error("Plangenerierung für Workflow {} ist technisch fehlgeschlagen (Fehlertyp {}).",
                    workflowId, exception.getClass().getSimpleName());
            var errorCode = errorClassifier.classify(exception);
            workflowService.recordTechnicalFailure(
                    workflowId, errorCode, false, errorClassifier.diagnosis(errorCode));
        }
    }
}
