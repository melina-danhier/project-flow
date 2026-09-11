package de.melinadanhier.projectflow.planelement.service;

import de.melinadanhier.projectflow.common.exception.ConflictException;
import de.melinadanhier.projectflow.common.exception.DomainValidationException;
import de.melinadanhier.projectflow.common.exception.ResourceNotFoundException;
import de.melinadanhier.projectflow.plancontainer.project.model.Project;
import de.melinadanhier.projectflow.plancontainer.project.service.ProjectAuthorizationService;
import de.melinadanhier.projectflow.planelement.dto.PlanElementMoveForm;
import de.melinadanhier.projectflow.planelement.dto.PlanSectionMoveForm;
import de.melinadanhier.projectflow.planelement.dto.PlanSortModeForm;
import de.melinadanhier.projectflow.planelement.model.Milestone;
import de.melinadanhier.projectflow.planelement.model.PlanElement;
import de.melinadanhier.projectflow.planelement.model.PlanSection;
import de.melinadanhier.projectflow.planelement.model.Task;
import de.melinadanhier.projectflow.planelement.repository.PlanElementRepository;
import de.melinadanhier.projectflow.planelement.repository.PlanSectionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectPlanOrderingService {

    private final ProjectAuthorizationService authorizationService;
    private final PlanElementRepository elementRepository;
    private final PlanSectionRepository sectionRepository;
    private final EntityManager entityManager;

    @Transactional
    public void updateSortMode(UUID projectId, UUID userId, PlanSortModeForm form) {
        Project project = editableProject(projectId, userId, form.getProjectLockVersion());
        project.setSortMode(form.getSortMode());
    }

    @Transactional
    public void moveSection(UUID projectId, UUID sectionId, UUID userId, PlanSectionMoveForm form) {
        editableProject(projectId, userId, form.getProjectLockVersion());
        PlanSection moved = sectionRepository.findByIdAndPlanContainerId(sectionId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Projektbereich wurde nicht gefunden."));
        List<PlanSection> sections = new ArrayList<>(
                sectionRepository.findAllByPlanContainerIdOrderBySortOrderAsc(projectId));
        sections.remove(moved);
        PlanOrdering.place(sections, moved, form.getTargetPosition(),
                PlanSection::getSortOrder, PlanSection::setSortOrder);
    }

    @Transactional
    public void moveElement(UUID projectId, UUID elementId, UUID userId, PlanElementMoveForm form) {
        editableProject(projectId, userId, form.getProjectLockVersion());
        PlanElement moved = elementRepository.findByIdAndPlanContainerId(elementId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Planelement wurde nicht gefunden."));
        PlanSection target = form.getTargetSectionId() == null ? null
                : sectionRepository.findByIdAndPlanContainerId(form.getTargetSectionId(), projectId)
                        .orElseThrow(() -> new ResourceNotFoundException("Projektbereich wurde nicht gefunden."));

        List<PlanElement> targetOrder = loadElements(projectId, target);
        targetOrder.remove(moved);
        moved.setPlanSection(target);
        PlanOrdering.place(targetOrder, moved, form.getTargetPosition(),
                PlanElement::getSortOrder, PlanElement::setSortOrder);
    }

    @Transactional
    public void updateElementDate(UUID projectId, UUID elementId, UUID userId,
                                  LocalDate targetDate, long projectLockVersion) {
        editableProject(projectId, userId, projectLockVersion);
        PlanElement element = elementRepository.findByIdAndPlanContainerId(elementId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Planelement wurde nicht gefunden."));
        if (element instanceof Task task) {
            if (task.getStartDate() != null && targetDate.isBefore(task.getStartDate())) {
                throw new DomainValidationException("Das Fälligkeitsdatum darf nicht vor dem Startdatum liegen.");
            }
            task.setDueDate(targetDate);
            task.setRelativeDueDay(null);
        } else if (element instanceof Milestone milestone) {
            milestone.setDueDate(targetDate);
            milestone.setRelativeDueDay(null);
        }
    }

    private Project editableProject(UUID projectId, UUID userId, Long submittedVersion) {
        Project project = authorizationService.requireEditableMemberForUpdate(projectId, userId).getProject();
        if (submittedVersion == null || submittedVersion != project.getLockVersion()) {
            throw new ConflictException("Der Projektplan wurde zwischenzeitlich geändert. Bitte lade die Seite neu.");
        }
        entityManager.lock(project, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        return project;
    }

    private List<PlanElement> loadElements(UUID projectId, PlanSection section) {
        return new ArrayList<>(section == null
                ? elementRepository.findAllByPlanContainerIdAndPlanSectionIsNullOrderBySortOrderAsc(projectId)
                : elementRepository.findAllByPlanContainerIdAndPlanSectionIdOrderBySortOrderAsc(
                        projectId, section.getId()));
    }

}
