package de.melinadanhier.projectflow.draft.service;

import de.melinadanhier.projectflow.common.exception.ConflictException;
import de.melinadanhier.projectflow.draft.dto.DraftReviewDto;
import de.melinadanhier.projectflow.draft.dto.DraftSectionForm;
import de.melinadanhier.projectflow.draft.dto.DraftMilestoneForm;
import de.melinadanhier.projectflow.draft.dto.DraftElementMoveForm;
import de.melinadanhier.projectflow.draft.dto.DraftSectionMoveForm;
import de.melinadanhier.projectflow.draft.dto.DraftSortModeForm;
import de.melinadanhier.projectflow.draft.mapper.DraftMapper;
import de.melinadanhier.projectflow.draft.model.DraftPlan;
import de.melinadanhier.projectflow.draft.model.DraftPlanStatus;
import de.melinadanhier.projectflow.draft.model.DraftReviewStatus;
import de.melinadanhier.projectflow.draft.model.DraftTask;
import de.melinadanhier.projectflow.draft.model.DraftMilestone;
import de.melinadanhier.projectflow.draft.model.DraftPlanElement;
import de.melinadanhier.projectflow.draft.model.DraftSection;
import de.melinadanhier.projectflow.draft.dto.DraftTaskForm;
import de.melinadanhier.projectflow.common.exception.ResourceNotFoundException;
import de.melinadanhier.projectflow.common.exception.DomainValidationException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.validation.Validator;
import de.melinadanhier.projectflow.draft.repository.PlanDraftRepository;
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

@Service
@RequiredArgsConstructor
public class DraftReviewService {

    private final PlanDraftRepository planDraftRepository;
    private final DraftMapper draftMapper;
    private final ProjectAuthorizationService authorizationService;
    private final EntityManager entityManager;
    private final Validator validator;
    private final DraftValidationService validationService;

    @Transactional(readOnly = true)
    public DraftReviewDto review(UUID projectId, UUID userId) {
        authorizationService.requireOwner(projectId, userId);
        DraftPlan draft = planDraftRepository.findByProjectId(projectId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Für dieses Projekt ist kein Planentwurf vorhanden."
                ));
        DraftReviewDto review = draftMapper.toReviewDto(draft);
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
                            .map(element -> {
                                var elementDto = draftMapper.toDto(element);
                                elementDto.setManualPosition(manualPositions.get(element.getId()));
                                return elementDto;
                            }).toList());
                    return dto;
                }).toList());
        review.setElements(draft.getElements().stream().map(draftMapper::toDto).toList());
        var project = draft.getProject();
        review.setCategoryLabel(project.getSubcategory() != null
                ? project.getSubcategory().getLabel()
                : categoryLabel(project.getCategory()));
        return review;
    }

    @Transactional
    public void acceptElement(UUID projectId, UUID elementId, UUID userId, long version) {
        DraftPlan draft = editable(projectId, userId, version);
        var element = draft.getElements().stream().filter(value -> value.getId().equals(elementId))
                .findFirst().orElseThrow(() -> new ResourceNotFoundException("Entwurfselement nicht gefunden."));
        element.setReviewStatus(DraftReviewStatus.ACCEPTED);
    }

    @Transactional
    public void acceptSection(UUID projectId, UUID sectionId, UUID userId, long version) {
        DraftPlan draft = editable(projectId, userId, version);
        var section = draft.getSections().stream().filter(value -> value.getId().equals(sectionId))
                .findFirst().orElseThrow(() -> new ResourceNotFoundException("Entwurfsbereich nicht gefunden."));
        section.setReviewStatus(DraftReviewStatus.ACCEPTED);
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
        String description = normalize(form.getDescription());
        if (!Objects.equals(section.getTitle(), title) || !Objects.equals(section.getDescription(), description)) {
            section.setTitle(title);
            section.setDescription(description);
            section.markContentModified();
        }
        section.setReviewStatus(DraftReviewStatus.PENDING);
    }

    @Transactional
    public void updateTask(UUID projectId, UUID taskId, UUID userId, DraftTaskForm form) {
        authorizationService.requireOwner(projectId, userId);
        if (!validator.validate(form).isEmpty()) {
            throw new DomainValidationException("Bitte prüfe die Aufgabenangaben.");
        }
        DraftPlan draft = editable(projectId, userId, form.getLockVersion());
        DraftTask task = task(draft, taskId);
        String title = form.getTitle().strip();
        String description = normalize(form.getDescription());
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
        task.setReviewStatus(DraftReviewStatus.PENDING);
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
        milestone.setReviewStatus(DraftReviewStatus.PENDING);
        validationService.validate(draft);
    }

    @Transactional
    public void moveElement(UUID projectId, UUID elementId, UUID userId, DraftElementMoveForm form) {
        requireValid(form, "Die Zielposition ist ungültig.");
        DraftPlan draft = editable(projectId, userId, form.getLockVersion());
        DraftPlanElement element = element(draft, elementId);
        DraftSection source = element.getDraftSection();
        if (source == null) {
            throw new DomainValidationException("Das Entwurfselement ist keinem Bereich zugeordnet.");
        }
        DraftSection target = section(draft, form.getTargetSectionId());
        List<DraftPlanElement> sourceOrder = manualOrder(source);
        int currentPosition = sourceOrder.indexOf(element);
        boolean sameSection = source.getId().equals(target.getId());
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
            List<DraftPlanElement> targetOrder = manualOrder(target);
            insert(targetOrder, element, form.getTargetPosition());
            source.removeElement(element);
            target.addElement(element);
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
        section.getElements().clear();
        section.getElements().addAll(order);
        for (int index = 0; index < order.size(); index++) order.get(index).setSortOrder(index);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private void requireValid(Object form, String message) {
        if (!validator.validate(form).isEmpty()) throw new DomainValidationException(message);
    }

    private DraftPlan editable(UUID projectId, UUID userId, long version) {
        authorizationService.requireOwner(projectId, userId);
        DraftPlan draft = planDraftRepository.findForUpdateByProjectId(projectId)
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
