package de.melinadanhier.projectflow.draft.service;

import de.melinadanhier.projectflow.common.exception.ConflictException;
import de.melinadanhier.projectflow.common.exception.ResourceNotFoundException;
import de.melinadanhier.projectflow.draft.mapper.GeneratedPlanDraftMapper.MappedDraft;
import de.melinadanhier.projectflow.draft.model.DraftPlan;
import de.melinadanhier.projectflow.draft.model.DraftPlanStatus;
import de.melinadanhier.projectflow.draft.repository.PlanDraftRepository;
import de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflowStatus;
import de.melinadanhier.projectflow.generation.repository.AiPlanGenerationWorkflowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlanDraftMaterializationService {

    private final PlanDraftRepository planDraftRepository;
    private final AiPlanGenerationWorkflowRepository workflowRepository;
    private final Clock clock;

    /**
     * The graph and both success states commit (or roll back) together.
     * The workflow coordinator calls this without a transaction. Direct transactional
     * callers retain ownership of the commit; materialization must not commit independently.
     */
    @Transactional
    public boolean materialize(UUID workflowId, MappedDraft contents,
                               String serializedPlan, boolean assumptionsNeedReview) {
        var workflow = workflowRepository.findByIdForUpdate(workflowId)
                .orElseThrow(() -> new ResourceNotFoundException("KI-Workflow wurde nicht gefunden."));
        if (workflow.getStatus() != AiPlanGenerationWorkflowStatus.GENERATION_RUNNING) {
            return false;
        }
        UUID projectId = workflow.getProject().getId();
        var existingDraft = planDraftRepository.findByProjectId(projectId);
        if (existingDraft.isPresent() && workflow.getPendingAssumptionReview() == null) {
            throw new ConflictException("Für dieses Projekt existiert bereits ein Planentwurf.");
        }
        DraftPlan draft = existingDraft.orElseGet(() -> {
            DraftPlan created = new DraftPlan();
            workflow.getProject().attachDraft(created);
            return created;
        });
        draft.clearContents();
        draft.setSortMode(workflow.getProject().getSortMode());
        contents.sections().forEach(draft::addSection);
        contents.elements().forEach(draft::addElement);
        draft.setGeneratedAt(Instant.now(clock));
        draft.setAppliedAt(null);
        draft.setStatus(DraftPlanStatus.READY_FOR_REVIEW);
        workflow.recordGenerationCompleted(serializedPlan, assumptionsNeedReview);
        // Cascade persists sections and elements; prerequisite links reference these same task entities.
        planDraftRepository.saveAndFlush(draft);
        return true;
    }
}
