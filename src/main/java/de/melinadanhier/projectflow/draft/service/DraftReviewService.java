package de.melinadanhier.projectflow.draft.service;

import de.melinadanhier.projectflow.common.exception.ConflictException;
import de.melinadanhier.projectflow.draft.dto.DraftReviewDto;
import de.melinadanhier.projectflow.draft.dto.DraftSectionForm;
import de.melinadanhier.projectflow.draft.mapper.DraftMapper;
import de.melinadanhier.projectflow.draft.model.DraftPlan;
import de.melinadanhier.projectflow.draft.model.DraftPlanStatus;
import de.melinadanhier.projectflow.draft.model.DraftReviewStatus;
import de.melinadanhier.projectflow.draft.model.DraftTask;
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

@Service
@RequiredArgsConstructor
public class DraftReviewService {

    private final PlanDraftRepository planDraftRepository;
    private final DraftMapper draftMapper;
    private final ProjectAuthorizationService authorizationService;
    private final EntityManager entityManager;
    private final Validator validator;

    @Transactional(readOnly = true)
    public DraftReviewDto review(UUID projectId, UUID userId) {
        authorizationService.requireOwner(projectId, userId);
        DraftPlan draft = planDraftRepository.findByProjectId(projectId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Für dieses Projekt ist kein Planentwurf vorhanden."
                ));
        DraftReviewDto review = draftMapper.toReviewDto(draft);
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
        section.setTitle(form.getTitle().strip());
        section.setDescription(form.getDescription() == null || form.getDescription().isBlank()
                ? null : form.getDescription().strip());
        section.setUserModified(true);
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
        task.setTitle(form.getTitle().strip());
        task.setDescription(form.getDescription() == null || form.getDescription().isBlank()
                ? null : form.getDescription().strip());
        task.setStartDate(form.getStartDate());
        task.setDueDate(form.getDueDate());
        task.setEstimatedHours(form.getEstimatedHours());
        task.setPriority(form.getPriority());
        task.setUserModified(true);
        task.setReviewStatus(DraftReviewStatus.PENDING);
        // The assumption is immutable in the review form, including forged request parameters.
    }

    @Transactional
    public void deleteTask(UUID projectId, UUID taskId, UUID userId, long version) {
        DraftPlan draft = editable(projectId, userId, version);
        DraftTask task = task(draft, taskId);
        draft.getElements().stream().filter(DraftTask.class::isInstance).map(DraftTask.class::cast)
                .forEach(other -> other.removePrerequisite(task));
        task.getPrerequisites().clear();
        if (task.getDraftSection() != null) task.getDraftSection().removeElement(task);
        draft.removeElement(task);
    }

    private DraftTask task(DraftPlan draft, UUID taskId) {
        return draft.getElements().stream().filter(DraftTask.class::isInstance).map(DraftTask.class::cast)
                .filter(task -> task.getId().equals(taskId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Entwurfsaufgabe nicht gefunden."));
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
