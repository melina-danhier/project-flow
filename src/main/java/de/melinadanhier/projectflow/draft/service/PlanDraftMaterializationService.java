package de.melinadanhier.projectflow.draft.service;

import de.melinadanhier.projectflow.ai.model.AiSchemaVersions;
import de.melinadanhier.projectflow.ai.model.generation.GeneratedPlanResponse;
import de.melinadanhier.projectflow.ai.prompt.AiPromptVersions;
import de.melinadanhier.projectflow.draft.model.DraftMilestone;
import de.melinadanhier.projectflow.draft.model.DraftPlan;
import de.melinadanhier.projectflow.draft.model.DraftPlanStatus;
import de.melinadanhier.projectflow.draft.model.DraftSection;
import de.melinadanhier.projectflow.draft.model.DraftTask;
import de.melinadanhier.projectflow.draft.repository.PlanDraftRepository;
import de.melinadanhier.projectflow.plancontainer.project.model.Project;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PlanDraftMaterializationService {

    private final PlanDraftRepository planDraftRepository;
    @Transactional
    public DraftPlan materialize(Project project, GeneratedPlanResponse response) {
        DraftPlan draft = planDraftRepository.findByProjectId(project.getId()).orElseGet(DraftPlan::new);
        if (draft.getId() != null && draft.getStatus() == DraftPlanStatus.READY_FOR_REVIEW) {
            return draft;
        }

        removeExistingContents(draft);
        draft.setProject(project);
        draft.setStatus(DraftPlanStatus.GENERATING);
        draft.setAttemptCount(draft.getAttemptCount() + 1);
        draft.setPromptVersion(AiPromptVersions.GENERATION_PROMPT);
        draft.setSchemaVersion(AiSchemaVersions.GENERATED_PLAN);
        Map<String, DraftTask> draftTasksByTempId = new LinkedHashMap<>();
        response.phases().forEach(phase -> {
            DraftSection section = new DraftSection();
            section.setTitle(phase.title());
            section.setDescription(phase.description());
            section.setStartDate(phase.startDate());
            section.setEndDate(phase.endDate());
            section.setSortOrder(phase.order());
            draft.addSection(section);

            phase.tasks().forEach(generated -> {
                DraftTask task = new DraftTask();
                task.setTitle(generated.title());
                task.setDescription(generated.description());
                task.setStartDate(generated.startDate());
                task.setDueDate(generated.dueDate());
                task.setEstimatedHours(generated.estimatedHours());
                task.setPriority(generated.priority() == null
                        ? de.melinadanhier.projectflow.planelement.model.TaskPriority.MEDIUM
                        : generated.priority());
                task.setSortOrder(generated.order());
                task.setCriticalAssumption(generated.criticalAssumption());
                task.setHasCriticalAssumption(generated.criticalAssumption() != null);
                task.setAiOrigin(generated.origin());
                draft.addElement(task);
                section.addElement(task);
                draftTasksByTempId.put(generated.tempId(), task);
            });

            phase.milestones().forEach(generated -> {
                DraftMilestone milestone = new DraftMilestone();
                milestone.setTitle(generated.title());
                milestone.setDueDate(generated.date());
                milestone.setSortOrder(generated.order());
                milestone.setAiOrigin(de.melinadanhier.projectflow.ai.model.generation.GeneratedElementOrigin.AI_INFERRED);
                draft.addElement(milestone);
                section.addElement(milestone);
            });
        });

        response.phases().stream().flatMap(phase -> phase.tasks().stream()).forEach(generated -> {
            DraftTask successor = draftTasksByTempId.get(generated.tempId());
            generated.prerequisiteTaskTempIds().forEach(prerequisiteId ->
                    successor.addPrerequisite(draftTasksByTempId.get(prerequisiteId)));
        });

        draft.setGeneratedAt(Instant.now());
        draft.setStatus(DraftPlanStatus.READY_FOR_REVIEW);
        project.attachDraft(draft);
        return planDraftRepository.save(draft);
    }

    private void removeExistingContents(DraftPlan draft) {
        List.copyOf(draft.getElements()).forEach(element -> {
            if (element.getDraftSection() != null) {
                element.getDraftSection().removeElement(element);
            }
            draft.removeElement(element);
        });
        List.copyOf(draft.getSections()).forEach(draft::removeSection);
    }
}
