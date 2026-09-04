package de.melinadanhier.projectflow.draft.service;

import de.melinadanhier.projectflow.common.exception.ConflictException;
import de.melinadanhier.projectflow.draft.dto.editing.DraftElementMoveForm;
import de.melinadanhier.projectflow.draft.dto.editing.DraftMilestoneForm;
import de.melinadanhier.projectflow.draft.dto.editing.DraftSectionForm;
import de.melinadanhier.projectflow.draft.dto.editing.DraftSectionMoveForm;
import de.melinadanhier.projectflow.draft.dto.editing.DraftSortModeForm;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.time.LocalDate;
import de.melinadanhier.projectflow.plancontainer.model.SortMode;
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
        authorizationService.requireOwner(projectId, userId);
        DraftPlan draft = draftRepository.findByProjectId(projectId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Für dieses Projekt ist kein Planentwurf vorhanden."
                ));
        DraftReviewDto review = draftMapper.toReviewDto(draft);
        review.setActiveReviewStatus(reviewStatus);
        review.setTotalElementCount(draft.getSections().size() + draft.getElements().size());
        review.setReviewedElementCount((int) java.util.stream.Stream.concat(
                        draft.getSections().stream().map(DraftSection::getReviewStatus),
                        draft.getElements().stream().map(DraftPlanElement::getReviewStatus))
                .filter(status -> status != DraftReviewStatus.PENDING).count());
        review.setSections(draft.getSections().stream()
                .sorted(Comparator.comparingInt(DraftSection::getSortOrder).thenComparing(DraftSection::getId))
                .map(section -> {
                    var dto = draftMapper.toDto(section);
                    List<DraftPlanElement> manualOrder = manualOrder(section);
                    var manualPositions = new java.util.HashMap<UUID, Integer>();
                    for (int position = 0; position < manualOrder.size(); position++) {
                        manualPositions.put(manualOrder.get(position).getId(), position);
                    }
                    dto.setElements(displayOrder(manualOrder, draft.getSortMode()).stream()
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
                .sorted(Comparator.comparingInt(DraftPlanElement::getSortOrder).thenComparing(DraftPlanElement::getId))
                .toList();
        var unsectionedPositions = new java.util.HashMap<UUID, Integer>();
        for (int position = 0; position < unsectioned.size(); position++) {
            unsectionedPositions.put(unsectioned.get(position).getId(), position);
        }
        review.setUnsectionedElements(displayOrder(unsectioned, draft.getSortMode()).stream()
                .filter(element -> matches(element, reviewStatus))
                .map(element -> {
                    var dto = draftMapper.toDto(element);
                    dto.setManualPosition(unsectionedPositions.get(element.getId()));
                    return dto;
                }).toList());
        var project = draft.getProject();
        review.setCategoryLabel(project.getSubcategory() != null
                ? project.getSubcategory().getLabel()
                : categoryLabel(project.getCategory()));
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
                || task.getPriority() != form.getPriority();
        task.setTitle(title);
        task.setDescription(description);
        task.setStartDate(form.getStartDate());
        task.setDueDate(form.getDueDate());
        task.setEstimatedHours(form.getEstimatedHours());
        task.setPriority(form.getPriority());
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
        boolean changed = !Objects.equals(milestone.getTitle(), title)
                || !Objects.equals(milestone.getDueDate(), form.getDueDate());
        milestone.setTitle(title);
        milestone.setDueDate(form.getDueDate());
        if (changed) milestone.markContentModified();
        validationService.validate(draft);
    }

    @Transactional
    public void moveElement(UUID projectId, UUID elementId, UUID userId, DraftElementMoveForm form) {
        requireValid(form, "Die Zielposition ist ungültig.");
        DraftPlan draft = editable(projectId, userId, form.getLockVersion());
        DraftPlanElement element = element(draft, elementId);
        DraftSection source = element.getDraftSection();
        DraftSection target = form.getTargetSectionId() == null ? null : section(draft, form.getTargetSectionId());
        List<DraftPlanElement> sourceOrder = manualOrder(draft, source);
        int currentPosition = sourceOrder.indexOf(element);
        boolean sameSection = Objects.equals(source == null ? null : source.getId(),
                target == null ? null : target.getId());
        if (sameSection && draft.getSortMode() == SortMode.DATE && isDated(element)
                && form.getTargetPosition() != currentPosition) {
            throw new DomainValidationException(
                    "Datierte Elemente werden bei zeitlicher Sortierung automatisch eingeordnet.");
        }
        sourceOrder.remove(element);
        if (sameSection) {
            insert(sourceOrder, element, form.getTargetPosition());
            applyOrder(source, sourceOrder);
        } else {
            List<DraftPlanElement> targetOrder = manualOrder(draft, target);
            insert(targetOrder, element, form.getTargetPosition());
            if (source != null) source.removeElement(element);
            if (target != null) target.addElement(element);
            applyOrder(source, sourceOrder);
            applyOrder(target, targetOrder);
        }
    }

    @Transactional
    public void moveSection(UUID projectId, UUID sectionId, UUID userId, DraftSectionMoveForm form) {
        requireValid(form, "Die Zielposition ist ungültig.");
        DraftPlan draft = editable(projectId, userId, form.getLockVersion());
        DraftSection moved = section(draft, sectionId);
        List<DraftSection> order = new ArrayList<>(draft.getSections());
        order.sort(Comparator.comparingInt(DraftSection::getSortOrder).thenComparing(DraftSection::getId));
        order.remove(moved);
        if (form.getTargetPosition() > order.size()) {
            throw new DomainValidationException("Die Zielposition des Bereichs ist ungültig.");
        }
        order.add(form.getTargetPosition(), moved);
        for (int index = 0; index < order.size(); index++) order.get(index).setSortOrder(index);
    }

    @Transactional
    public void updateSortMode(UUID projectId, UUID userId, DraftSortModeForm form) {
        requireValid(form, "Der Sortiermodus ist ungültig.");
        DraftPlan draft = editable(projectId, userId, form.getLockVersion());
        draft.setSortMode(form.getSortMode());
    }

    @Transactional
    public void deleteTask(UUID projectId, UUID taskId, UUID userId, long version) {
        DraftPlan draft = editable(projectId, userId, version);
        DraftTask task = task(draft, taskId);
        DraftSection section = task.getDraftSection();
        List<DraftPlanElement> remaining = section == null ? new ArrayList<>() : manualOrder(section);
        remaining.remove(task);
        draft.getElements().stream().filter(DraftTask.class::isInstance).map(DraftTask.class::cast)
                .forEach(other -> other.removePrerequisite(task));
        task.getPrerequisites().clear();
        if (section != null) section.removeElement(task);
        draft.removeElement(task);
        if (section != null) applyOrder(section, remaining);
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
        order.sort(Comparator.comparingInt(DraftPlanElement::getSortOrder)
                .thenComparing(DraftPlanElement::getId));
        return order;
    }

    private List<DraftPlanElement> manualOrder(DraftPlan draft, DraftSection section) {
        if (section != null) return manualOrder(section);
        return draft.getElements().stream().filter(element -> element.getDraftSection() == null)
                .sorted(Comparator.comparingInt(DraftPlanElement::getSortOrder)
                        .thenComparing(DraftPlanElement::getId))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private List<DraftPlanElement> displayOrder(List<DraftPlanElement> manual, SortMode mode) {
        if (mode != SortMode.DATE) return manual;
        List<DraftPlanElement> dated = manual.stream().filter(this::isDated)
                .sorted(Comparator.comparing(this::date)).toList();
        var iterator = dated.iterator();
        return manual.stream().map(element -> isDated(element) ? iterator.next() : element).toList();
    }

    private boolean isDated(DraftPlanElement element) { return date(element) != null; }

    private LocalDate date(DraftPlanElement element) {
        if (element instanceof DraftTask task) return task.getDueDate();
        if (element instanceof DraftMilestone milestone) return milestone.getDueDate();
        return null;
    }

    private void insert(List<DraftPlanElement> order, DraftPlanElement element, int position) {
        if (position > order.size()) throw new DomainValidationException("Die Zielposition ist ungültig.");
        order.add(position, element);
    }

    private void applyOrder(DraftSection section, List<DraftPlanElement> order) {
        if (section != null) {
            section.getElements().clear();
            section.getElements().addAll(order);
        }
        for (int index = 0; index < order.size(); index++) order.get(index).setSortOrder(index);
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
        authorizationService.requireOwner(projectId, userId);
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

    private String categoryLabel(de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory category) {
        if (category == null) {
            return null;
        }
        return switch (category) {
            case EDUCATION -> "Bildung und Studium";
            case SOFTWARE_TECHNOLOGY -> "Software und Technik";
            case EVENT -> "Veranstaltung";
            case HOME -> "Zuhause";
            case CREATIVE -> "Kreativprojekt";
            case CAREER -> "Beruf und Karriere";
            case HEALTH_PERSONAL_DEVELOPMENT -> "Gesundheit und persönliche Entwicklung";
            case TRAVEL -> "Reise";
            case OTHER -> "Sonstiges";
        };
    }
}
