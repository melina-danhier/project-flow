package de.melinadanhier.projectflow.wizard.service;

import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;
import de.melinadanhier.projectflow.generation.model.workflow.AiWorkflowCompletion;
import de.melinadanhier.projectflow.generation.repository.AiPlanGenerationWorkflowRepository;
import de.melinadanhier.projectflow.generation.repository.AiWorkflowCompletionTokenRepository;
import de.melinadanhier.projectflow.generation.service.workflow.AiWorkflowInitializationService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class AiWizardCompletionService {

    private final AiPlanGenerationWorkflowRepository workflowRepository;
    private final AiWorkflowCompletionTokenRepository completionTokenRepository;
    private final AiWorkflowInitializationService persistenceService;

    public AiWorkflowCompletion complete(
            UUID completionToken,
            UUID userId,
            Supplier<AiWizardSnapshot> snapshotSupplier
    ) {
        return complete(completionToken, userId, snapshotSupplier, null);
    }

    public AiWorkflowCompletion complete(
            UUID completionToken,
            UUID userId,
            Supplier<AiWizardSnapshot> snapshotSupplier,
            UUID editingWorkflowId
    ) {
        return findExisting(completionToken, userId).orElseGet(() -> {
            AiWizardSnapshot snapshot;
            try {
                snapshot = snapshotSupplier.get();
            } catch (RuntimeException exception) {
                return findExisting(completionToken, userId).orElseThrow(() -> exception);
            }
            try {
                return editingWorkflowId == null
                        ? persistenceService.create(snapshot, completionToken, userId)
                        : persistenceService.restart(
                                editingWorkflowId, snapshot, completionToken, userId);
            } catch (DataIntegrityViolationException exception) {
                return findExisting(completionToken, userId).orElseThrow(() -> exception);
            }
        });
    }

    private Optional<AiWorkflowCompletion> findExisting(UUID completionToken, UUID userId) {
        return completionTokenRepository.findOwnedWorkflowIdByToken(completionToken, userId)
                .flatMap(workflowRepository::findById)
                .map(workflow -> new AiWorkflowCompletion(
                        workflow.getId(), workflow.getProject().getId()));
    }
}
