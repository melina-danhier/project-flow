package de.melinadanhier.projectflow.draft.service;

import de.melinadanhier.projectflow.common.exception.ConflictException;
import de.melinadanhier.projectflow.draft.dto.editing.DraftElementMoveForm;
import de.melinadanhier.projectflow.draft.dto.editing.DraftMilestoneForm;
import de.melinadanhier.projectflow.draft.dto.editing.DraftSectionForm;
import de.melinadanhier.projectflow.draft.dto.editing.DraftSectionMoveForm;
import de.melinadanhier.projectflow.draft.dto.review.DraftReviewDto;
import de.melinadanhier.projectflow.draft.dto.review.DraftSectionDto;
import de.melinadanhier.projectflow.draft.mapper.DraftMapper;
import de.melinadanhier.projectflow.draft.model.DraftPlan;
import de.melinadanhier.projectflow.draft.model.DraftPlanStatus;
import de.melinadanhier.projectflow.draft.model.DraftReviewStatus;
import de.melinadanhier.projectflow.draft.model.DraftTask;
import de.melinadanhier.projectflow.draft.model.DraftMilestone;
import de.melinadanhier.projectflow.draft.model.DraftPlanElement;
import de.melinadanhier.projectflow.draft.model.DraftSection;
import de.melinadanhier.projectflow.draft.dto.editing.DraftTaskForm;
import de.melinadanhier.projectflow.common.exception.ResourceNotFoundException;
import de.melinadanhier.projectflow.common.exception.DomainValidationException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.validation.Validator;
import de.melinadanhier.projectflow.draft.repository.DraftRepository;
import de.melinadanhier.projectflow.plancontainer.project.service.ProjectAuthorizationService;
import de.melinadanhier.projectflow.plancontainer.project.model.membership.ProjectMemberRole;
import de.melinadanhier.projectflow.planelement.service.PlanOrdering;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.time.LocalDate;
import de.melinadanhier.projectflow.generation.repository.AiPlanGenerationWorkflowRepository;
import de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflowStatus;

@Service
@RequiredArgsConstructor
public class DraftReviewService {

    private final DraftRepository draftRepository;
    private final DraftMapper draftMapper;
    private final ProjectAuthorizationService authorizationService;
    private final EntityManager entityManager;
    private final Validator validator;
    private final DraftValidationService validationService;
    private final AiPlanGenerationWorkflowRepository workflowRepository;

    @Transactional(readOnly = true)
    public DraftReviewDto review(UUID projectId, UUID userId) {
        return review(projectId, userId, null);
    }

    @Transactional(readOnly = true)
    public DraftReviewDto review(UUID projectId, UUID userId, DraftReviewStatus reviewStatus) {
        var membership = authorizationService.requireMember(projectId, userId);
        DraftPlan draft = draftRepository.findByProjectId(projectId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Für dieses Projekt ist kein Planentwurf vorhanden."
                ));
        DraftReviewDto review = draftMapper.toReviewDto(draft);
        review.setOwner(membership.getRole() == ProjectMemberRole.OWNER);
        review.setActiveReviewStatus(reviewStatus);
        review.setTotalElementCount(draft.getSections().size() + draft.getElements().size());
        review.setTotalEstimatedHours(draft.getElements().stream()
                .filter(de.melinadanhier.projectflow.draft.model.DraftTask.class::isInstance)
                .map(de.melinadanhier.projectflow.draft.model.DraftTask.class::cast)
                .map(de.melinadanhier.projectflow.draft.model.DraftTask::getEstimatedHours)
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum());
        review.setReviewedElementCount((int) java.util.stream.Stream.concat(
                        draft.getSections().stream().map(DraftSection::getReviewStatus),
                        draft.getElements().stream().map(DraftPlanElement::getReviewStatus))
                .filter(status -> status != DraftReviewStatus.PENDING).count());
        review.setSections(draft.getSections().stream()
                .sorted(PlanOrdering.manual(DraftSection::getSortOrder, DraftSection::getId))
                .map(section -> {
                    var dto = draftMapper.toDto(section);
                    List<DraftPlanElement> manualOrder = manualOrder(section);
                    var manualPositions = new java.util.HashMap<UUID, Integer>();
                    for (int position = 0; position < manualOrder.size(); position++) {
                        manualPositions.put(manualOrder.get(position).getId(), position);
                    }
                    dto.setElements(PlanOrdering.display(manualOrder, draft.getProject().getSortMode(), this::date).stream()
                            .filter(element -> matches(element, reviewStatus))
                            .map(element -> {
                                var elementDto = draftMapper.toDto(element);
                                elementDto.setManualPosition(manualPositions.get(element.getId()));
                                return elementDto;
                            }).toList());
                    return dto;
                })
                .filter(section -> matches(section, reviewStatus)
                        || !section.getElements().isEmpty())
                .toList());
        review.setElements(draft.getElements().stream()
                .filter(element -> matches(element, reviewStatus))
                .map(draftMapper::toDto).toList());
        List<DraftPlanElement> unsectioned = draft.getElements().stream()
                .filter(element -> element.getDraftSection() == null)
                .sorted(PlanOrdering.manual(DraftPlanElement::getSortOrder, DraftPlanElement::getId))
                .toList();
        var unsectionedPositions = new java.util.HashMap<UUID, Integer>();
        for (int position = 0; position < unsectioned.size(); position++) {
            unsectionedPositions.put(unsectioned.get(position).getId(), position);
        }
        review.setUnsectionedElements(PlanOrdering.display(unsectioned, draft.getProject().getSortMode(), this::date).stream()
                .filter(element -> matches(element, reviewStatus))
                .map(element -> {
                    var dto = draftMapper.toDto(element);
                    dto.setManualPosition(unsectionedPositions.get(element.getId()));
                    return dto;
                }).toList());
        var project = draft.getProject();
        review.setCategoryLabel(project.getDisplayCategory());
        return review;
    }

    @Transactional
    public void acceptElement(UUID projectId, UUID elementId, UUID userId, long version) {
        updateElementReviewStatus(projectId, elementId, userId, version, DraftReviewStatus.ACCEPTED);
    }

    @Transactional
    public void rejectElement(UUID projectId, UUID elementId, UUID userId, long version) {
        updateElementReviewStatus(projectId, elementId, userId, version, DraftReviewStatus.REJECTED);
    }

    @Transactional
    public void resetElement(UUID projectId, UUID elementId, UUID userId, long version) {
        updateElementReviewStatus(projectId, elementId, userId, version, DraftReviewStatus.PENDING);
    }

    @Transactional
    public void acceptSection(UUID projectId, UUID sectionId, UUID userId, long version) {
        updateSectionReviewStatus(projectId, sectionId, userId, version, DraftReviewStatus.ACCEPTED);
    }

    @Transactional
    public void rejectSection(UUID projectId, UUID sectionId, UUID userId, long version) {
        updateSectionReviewStatus(projectId, sectionId, userId, version, DraftReviewStatus.REJECTED);
    }

    @Transactional
    public void resetSection(UUID projectId, UUID sectionId, UUID userId, long version) {
        updateSectionReviewStatus(projectId, sectionId, userId, version, DraftReviewStatus.PENDING);
    }

    @Transactional
    public void updateSection(UUID projectId, UUID sectionId, UUID userId, DraftSectionForm form) {
        if (!validator.validate(form).isEmpty()) {
            throw new DomainValidationException("Bitte prüfe die Angaben zum Bereich.");
        }
        DraftPlan draft = editable(projectId, userId, form.getLockVersion());
        DraftSection section = draft.getSections().stream()
                .filter(candidate -> candidate.getId().equals(sectionId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Entwurfsbereich nicht gefunden."));
        String title = form.getTitle().strip();
        String description = form.getDescription() == null || form.getDescription().isBlank()
                ? null : form.getDescription().strip();
        if (!Objects.equals(section.getTitle(), title) || !Objects.equals(section.getDescription(), description)) {
            section.setTitle(title);
            section.setDescription(description);
            section.markContentModified();
        }
    }

    @Transactional
    public void updateTask(UUID projectId, UUID taskId, UUID userId, DraftTaskForm form) {
        if (!validator.validate(form).isEmpty()) {
            throw new DomainValidationException("Bitte prüfe die Aufgabenangaben.");
        }
        DraftPlan draft = editable(projectId, userId, form.getLockVersion());
        DraftTask task = task(draft, taskId);
        String title = form.getTitle().strip();
        String description = form.getDescription() == null || form.getDescription().isBlank()
                ? null : form.getDescription().strip();
        boolean changed = !Objects.equals(task.getTitle(), title)
                || !Objects.equals(task.getDescription(), description)
                || !Objects.equals(task.getStartDate(), form.getStartDate())
                || !Objects.equals(task.getDueDate(), form.getDueDate())
                || !Objects.equals(task.getEstimatedHours(), form.getEstimatedHours())
                || (form.isSectionSelectionPresent()
                        && !Objects.equals(task.getDraftSection() == null ? null : task.getDraftSection().getId(),
                        form.getDraftSectionId()))
                || task.getPriority() != form.getPriority();
        task.setTitle(title);
        task.setDescription(description);
        task.setStartDate(form.getStartDate());
        task.setDueDate(form.getDueDate());
        task.setEstimatedHours(form.getEstimatedHours());
        task.setPriority(form.getPriority());
        if (form.isSectionSelectionPresent()) moveToSection(draft, task, form.getDraftSectionId());
        if (changed) task.markContentModified();
        validationService.validate(draft);
        // The assumption is immutable in the review form, including forged request parameters.
    }

    @Transactional
    public void updateMilestone(UUID projectId, UUID milestoneId, UUID userId, DraftMilestoneForm form) {
        requireValid(form, "Bitte prüfe die Meilensteinangaben.");
        DraftPlan draft = editable(projectId, userId, form.getLockVersion());
        DraftMilestone milestone = milestone(draft, milestoneId);
        String title = form.getTitle().strip();
        String description = form.getDescription() == null || form.getDescription().isBlank()
                ? null : form.getDescription().strip();
        boolean changed = !Objects.equals(milestone.getTitle(), title)
                || !Objects.equals(milestone.getDescription(), description)
                || (form.isSectionSelectionPresent()
                        && !Objects.equals(milestone.getDraftSection() == null ? null : milestone.getDraftSection().getId(),
                        form.getDraftSectionId()))
                || !Objects.equals(milestone.getDueDate(), form.getDueDate());
        milestone.setTitle(title);
        milestone.setDescription(description);
        milestone.setDueDate(form.getDueDate());
        if (form.isSectionSelectionPresent()) moveToSection(draft, milestone, form.getDraftSectionId());
        if (changed) milestone.markContentModified();
        validationService.validate(draft);
    }

    private void moveToSection(DraftPlan draft, DraftPlanElement element, UUID targetSectionId) {
        DraftSection current = element.getDraftSection();
        DraftSection target = targetSectionId == null ? null : section(draft, targetSectionId);
        if (Objects.equals(current == null ? null : current.getId(), targetSectionId)) return;
        if (current != null) current.removeElement(element);
        if (target != null) target.addElement(element);
        List<DraftPlanElement> targetOrder = new ArrayList<>(manualOrder(draft, target));
        targetOrder.remove(element);
        PlanOrdering.place(targetOrder, element, targetOrder.size(),
                DraftPlanElement::getSortOrder, DraftPlanElement::setSortOrder);
    }

    @Transactional
    public void moveElement(UUID projectId, UUID elementId, UUID userId, DraftElementMoveForm form) {
        requireValid(form, "Die Zielposition ist ungültig.");
        DraftPlan draft = editable(projectId, userId, form.getLockVersion());
        DraftPlanElement element = element(draft, elementId);
        DraftSection source = element.getDraftSection();
        DraftSection target = form.getTargetSectionId() == null ? null : section(draft, form.getTargetSectionId());
        boolean sameSection = Objects.equals(source == null ? null : source.getId(),
                target == null ? null : target.getId());
        List<DraftPlanElement> targetGroup = new ArrayList<>(manualOrder(draft, target));
        targetGroup.remove(element);
        PlanOrdering.place(targetGroup, element, form.getTargetPosition(),
                DraftPlanElement::getSortOrder, DraftPlanElement::setSortOrder);
        if (!sameSection) {
            if (source != null) source.removeElement(element);
            if (target != null) target.addElement(element);
        }
    }

    @Transactional
    public void updateElementDate(UUID projectId, UUID elementId, UUID userId,
                                  LocalDate targetDate, long lockVersion) {
        DraftPlan draft = editable(projectId, userId, lockVersion);
        DraftPlanElement element = element(draft, elementId);
        if (element instanceof DraftTask task) {
            if (task.getStartDate() != null && targetDate.isBefore(task.getStartDate())) {
                throw new DomainValidationException("Das Fälligkeitsdatum darf nicht vor dem Startdatum liegen.");
            }
            task.setDueDate(targetDate);
        } else if (element instanceof DraftMilestone milestone) {
            milestone.setDueDate(targetDate);
        }
        element.markContentModified();
        validationService.validate(draft);
    }

    @Transactional
    public void moveSection(UUID projectId, UUID sectionId, UUID userId, DraftSectionMoveForm form) {
        requireValid(form, "Die Zielposition ist ungültig.");
        DraftPlan draft = editable(projectId, userId, form.getLockVersion());
        DraftSection moved = section(draft, sectionId);
        List<DraftSection> order = new ArrayList<>(draft.getSections());
        order.sort(PlanOrdering.manual(DraftSection::getSortOrder, DraftSection::getId));
        order.remove(moved);
        PlanOrdering.place(order, moved, form.getTargetPosition(),
                DraftSection::getSortOrder, DraftSection::setSortOrder);
    }

    @Transactional
    public void deleteTask(UUID projectId, UUID taskId, UUID userId, long version) {
        DraftPlan draft = editable(projectId, userId, version);
        DraftTask task = task(draft, taskId);
        DraftSection section = task.getDraftSection();
        draft.getElements().stream().filter(DraftTask.class::isInstance).map(DraftTask.class::cast)
                .forEach(other -> other.removePrerequisite(task));
        task.getPrerequisites().clear();
        if (section != null) section.removeElement(task);
        draft.removeElement(task);
    }

    private DraftTask task(DraftPlan draft, UUID taskId) {
        return draft.getElements().stream().filter(DraftTask.class::isInstance).map(DraftTask.class::cast)
                .filter(task -> task.getId().equals(taskId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Entwurfsaufgabe nicht gefunden."));
    }

    private DraftMilestone milestone(DraftPlan draft, UUID id) {
        DraftPlanElement element = element(draft, id);
        if (element instanceof DraftMilestone milestone) return milestone;
        throw new ResourceNotFoundException("Entwurfsmeilenstein nicht gefunden.");
    }

    private DraftPlanElement element(DraftPlan draft, UUID id) {
        return draft.getElements().stream().filter(candidate -> candidate.getId().equals(id)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Entwurfselement nicht gefunden."));
    }

    private DraftSection section(DraftPlan draft, UUID id) {
        return draft.getSections().stream().filter(candidate -> candidate.getId().equals(id)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Entwurfsbereich nicht gefunden."));
    }

    private List<DraftPlanElement> manualOrder(DraftSection section) {
        List<DraftPlanElement> order = new ArrayList<>(section.getElements());
        order.sort(PlanOrdering.manual(DraftPlanElement::getSortOrder, DraftPlanElement::getId));
        return order;
    }

    private List<DraftPlanElement> manualOrder(DraftPlan draft, DraftSection section) {
        if (section != null) return manualOrder(section);
        return draft.getElements().stream().filter(element -> element.getDraftSection() == null)
                .sorted(PlanOrdering.manual(DraftPlanElement::getSortOrder, DraftPlanElement::getId))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private LocalDate date(DraftPlanElement element) {
        if (element instanceof DraftTask task) return task.getDueDate();
        if (element instanceof DraftMilestone milestone) return milestone.getDueDate();
        return null;
    }

    private void updateElementReviewStatus(UUID projectId, UUID elementId, UUID userId, long version,
                                           DraftReviewStatus status) {
        element(editable(projectId, userId, version), elementId).setReviewStatus(status);
    }

    private void updateSectionReviewStatus(UUID projectId, UUID sectionId, UUID userId, long version,
                                           DraftReviewStatus status) {
        section(editable(projectId, userId, version), sectionId).setReviewStatus(status);
    }

    private boolean matches(DraftPlanElement element, DraftReviewStatus status) {
        return status == null || element.getReviewStatus() == status;
    }

    private boolean matches(DraftSectionDto section,
                            DraftReviewStatus status) {
        return status == null || section.getReviewStatus() == status;
    }

    private void requireValid(Object form, String message) {
        if (!validator.validate(form).isEmpty()) throw new DomainValidationException(message);
    }

    private DraftPlan editable(UUID projectId, UUID userId, long version) {
        authorizationService.requireMember(projectId, userId);
        requireReleasedDraft(projectId);
        DraftPlan draft = draftRepository.findForUpdateByProjectId(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Planentwurf nicht gefunden."));
        if (draft.getStatus() != DraftPlanStatus.READY_FOR_REVIEW && draft.getStatus() != DraftPlanStatus.IN_REVIEW) {
            throw new ConflictException("Dieser Entwurf kann nicht mehr bearbeitet werden.");
        }
        if (draft.getLockVersion() != version) {
            throw new ConflictException("Der Entwurf wurde zwischenzeitlich geändert. Bitte lade die Seite neu.");
        }
        draft.setStatus(DraftPlanStatus.IN_REVIEW);
        // Child edits must invalidate open confirmation forms, even when the status stays IN_REVIEW.
        entityManager.lock(draft, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        return draft;
    }

    private void requireReleasedDraft(UUID projectId) {
        workflowRepository.findByProjectId(projectId).ifPresent(workflow -> {
            if (workflow.getStatus() != AiPlanGenerationWorkflowStatus.GENERATION_COMPLETED) {
                throw new ConflictException("Bitte schließe zuerst die Prüfung der kritischen Annahmen ab.");
            }
        });
    }

}
