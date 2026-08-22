package de.melinadanhier.projectflow.generation.service;

import de.melinadanhier.projectflow.generation.mapper.DraftMapper;
import de.melinadanhier.projectflow.common.exception.ConflictException;
import de.melinadanhier.projectflow.generation.dto.response.DraftReviewDto;
import de.melinadanhier.projectflow.generation.dto.response.GeneratedPlanResponse;
import de.melinadanhier.projectflow.generation.model.*;
import de.melinadanhier.projectflow.generation.prompt.AiPromptVersions;
import de.melinadanhier.projectflow.generation.repository.AiPlanGenerationWorkflowRepository;
import de.melinadanhier.projectflow.generation.repository.DraftPlanElementRepository;
import de.melinadanhier.projectflow.generation.repository.DraftSectionRepository;
import de.melinadanhier.projectflow.generation.repository.PlanDraftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import de.melinadanhier.projectflow.plancontainer.project.model.Project;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectLocation;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectStatus;
import de.melinadanhier.projectflow.plancontainer.project.service.ProjectAuthorizationService;
import de.melinadanhier.projectflow.plancontainer.project.service.ProjectStateService;
import de.melinadanhier.projectflow.planelement.model.*;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DraftService {

    private final PlanDraftRepository planDraftRepository;
    private final DraftSectionRepository draftSectionRepository;
    private final DraftPlanElementRepository draftPlanElementRepository;
    private final DraftMapper draftMapper;
    private final AiPlanGenerationWorkflowRepository workflowRepository;
    private final ProjectAuthorizationService authorizationService;
    private final ProjectStateService projectStateService;
    private final ObjectMapper objectMapper;

    @Transactional
    public PlanDraft materialize(Project project, GeneratedPlanResponse response) {
        PlanDraft draft = planDraftRepository.findByProjectId(project.getId()).orElseGet(PlanDraft::new);
        if (draft.getId() != null && draft.getStatus() == PlanDraftStatus.READY_FOR_REVIEW) {
            return draft;
        }
        draft.getSections().clear();
        draft.getElements().clear();
        draft.setProject(project);
        draft.setStatus(PlanDraftStatus.GENERATING);
        draft.setAttemptCount(draft.getAttemptCount() + 1);
        draft.setPromptVersion(AiPromptVersions.GENERATION_PROMPT);
        draft.setSchemaVersion("generated-plan-v1");
        draft.setSummary(response.metadata().summary());
        draft.setAssumptions(objectMapper.writeValueAsString(response.metadata().assumptions()));

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
                task.setSortOrder(generated.order());
                task.setCriticalAssumption(generated.criticalAssumption());
                task.setHasCriticalAssumption(generated.criticalAssumption() != null);
                draft.addElement(task);
                section.addElement(task);
            });
            phase.milestones().forEach(generated -> {
                DraftMilestone milestone = new DraftMilestone();
                milestone.setTitle(generated.title());
                milestone.setDueDate(generated.date());
                milestone.setSortOrder(generated.order());
                draft.addElement(milestone);
                section.addElement(milestone);
            });
        });
        draft.setGeneratedAt(Instant.now());
        draft.setStatus(PlanDraftStatus.READY_FOR_REVIEW);
        project.attachDraft(draft);
        return planDraftRepository.save(draft);
    }

    @Transactional(readOnly = true)
    public DraftReviewDto review(UUID projectId, UUID userId) {
        PlanDraft draft = planDraftRepository.findByProjectId(projectId)
                .orElseThrow(() -> new ConflictException("Für dieses Projekt ist kein Planentwurf vorhanden."));
        authorizationService.requireDraftOwner(projectId, draft.getId(), userId);
        return draftMapper.toReviewDto(draft);
    }

    @Transactional
    public UUID apply(UUID projectId, UUID userId) {
        Project project = authorizationService.requireDraftOwner(
                planDraftRepository.findByProjectId(projectId)
                        .orElseThrow(() -> new ConflictException("Für dieses Projekt ist kein Planentwurf vorhanden."))
                        .getId(), userId).getProject();
        PlanDraft draft = project.getCurrentDraft();
        if (draft.getStatus() == PlanDraftStatus.APPLIED) {
            return projectId;
        }
        if (draft.getStatus() != PlanDraftStatus.READY_FOR_REVIEW
                && draft.getStatus() != PlanDraftStatus.IN_REVIEW) {
            throw new ConflictException("Der Planentwurf kann in diesem Zustand nicht übernommen werden.");
        }
        if (!project.getSections().isEmpty() || !project.getElements().isEmpty()) {
            throw new ConflictException("Der aktive Projektplan enthält bereits Inhalte.");
        }
        draft.setStatus(PlanDraftStatus.APPLYING);
        Map<DraftSection, PlanSection> sections = new HashMap<>();
        draft.getSections().forEach(source -> {
            PlanSection target = new PlanSection();
            target.setTitle(source.getTitle());
            target.setDescription(source.getDescription());
            target.setStartDate(source.getStartDate());
            target.setEndDate(source.getEndDate());
            target.setSortOrder(source.getSortOrder());
            target.setOrigin(ElementOrigin.AI);
            target.setHasCriticalAssumption(source.isHasCriticalAssumption());
            project.addSection(target);
            sections.put(source, target);
            source.setReviewStatus(ReviewStatus.ACCEPTED);
        });
        draft.getElements().forEach(source -> {
            PlanElement target = copy(source);
            project.addElement(target);
            if (source.getDraftSection() != null) {
                sections.get(source.getDraftSection()).addElement(target);
            }
            source.setReviewStatus(ReviewStatus.ACCEPTED);
        });
        projectStateService.changeState(project, ProjectStatus.ACTIVE, ProjectLocation.OVERVIEW);
        draft.setStatus(PlanDraftStatus.APPLIED);
        workflowRepository.findByProjectId(projectId)
                .ifPresent(workflow -> workflow.setStatus(AiPlanGenerationWorkflowStatus.DRAFT_APPLIED));
        return projectId;
    }

    private PlanElement copy(DraftPlanElement source) {
        PlanElement target;
        if (source instanceof DraftTask draftTask) {
            Task task = new Task();
            task.setPriority(draftTask.getPriority());
            task.setStartDate(draftTask.getStartDate());
            task.setDueDate(draftTask.getDueDate());
            task.setEstimatedHours(draftTask.getEstimatedHours());
            target = task;
        } else if (source instanceof DraftMilestone draftMilestone) {
            Milestone milestone = new Milestone();
            milestone.setDueDate(draftMilestone.getDueDate());
            target = milestone;
        } else {
            throw new IllegalStateException("Nicht unterstütztes Entwurfselement.");
        }
        target.setTitle(source.getTitle());
        target.setDescription(source.getDescription());
        target.setSortOrder(source.getSortOrder());
        target.setOrigin(ElementOrigin.AI);
        target.setHasCriticalAssumption(source.isHasCriticalAssumption());
        target.setCriticalAssumption(source.getCriticalAssumption());
        return target;
    }
}
