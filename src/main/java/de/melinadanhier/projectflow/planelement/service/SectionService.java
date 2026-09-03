package de.melinadanhier.projectflow.planelement.service;

import de.melinadanhier.projectflow.common.exception.ConflictException;
import de.melinadanhier.projectflow.common.exception.DomainValidationException;
import de.melinadanhier.projectflow.common.exception.ResourceNotFoundException;
import de.melinadanhier.projectflow.plancontainer.project.model.Project;
import de.melinadanhier.projectflow.plancontainer.project.service.ProjectAuthorizationService;
import de.melinadanhier.projectflow.planelement.dto.DeleteSectionForm;
import de.melinadanhier.projectflow.planelement.dto.SectionDeletionMode;
import de.melinadanhier.projectflow.planelement.dto.SectionDto;
import de.melinadanhier.projectflow.planelement.dto.SectionForm;
import de.melinadanhier.projectflow.planelement.mapper.PlanElementMapper;
import de.melinadanhier.projectflow.planelement.model.ElementOrigin;
import de.melinadanhier.projectflow.planelement.model.PlanElement;
import de.melinadanhier.projectflow.planelement.model.PlanSection;
import de.melinadanhier.projectflow.planelement.model.Task;
import de.melinadanhier.projectflow.planelement.repository.PlanElementRepository;
import de.melinadanhier.projectflow.planelement.repository.PlanSectionRepository;
import de.melinadanhier.projectflow.planelement.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SectionService {

    private final PlanSectionRepository planSectionRepository;
    private final PlanElementRepository planElementRepository;
    private final TaskRepository taskRepository;
    private final PlanElementMapper planElementMapper;
    private final ProjectAuthorizationService authorizationService;

    @Transactional
    public SectionDto createSection(UUID projectId, SectionForm form, UUID userId) {
        Project project = authorizationService.requireEditableMemberForUpdate(projectId, userId).getProject();
        PlanSection section = new PlanSection();
        section.setPlanContainer(project);
        section.setOrigin(ElementOrigin.USER);
        apply(section, form);
        List<PlanSection> sections = new ArrayList<>(
                planSectionRepository.findAllByPlanContainerIdOrderBySortOrderAsc(projectId));
        sections.add(form.getSortOrder() == null
                ? sections.size() : Math.min(form.getSortOrder(), sections.size()), section);
        resequenceSections(sections);
        return planElementMapper.toDto(planSectionRepository.save(section));
    }

    @Transactional
    public SectionDto updateSection(UUID projectId, UUID sectionId, SectionForm form, UUID userId) {
        authorizationService.requireEditableMemberForUpdate(projectId, userId);
        PlanSection section = requireSection(projectId, sectionId);
        requireCurrentVersion(section.getLockVersion(), form.getLockVersion());
        apply(section, form);
        List<PlanSection> sections = new ArrayList<>(
                planSectionRepository.findAllByPlanContainerIdOrderBySortOrderAsc(projectId));
        sections.removeIf(candidate -> candidate.getId().equals(sectionId));
        sections.add(form.getSortOrder() == null
                ? sections.size() : Math.min(form.getSortOrder(), sections.size()), section);
        resequenceSections(sections);
        return planElementMapper.toDto(section);
    }

    @Transactional
    public void deleteSection(UUID projectId, UUID sectionId, DeleteSectionForm form, UUID userId) {
        authorizationService.requireEditableMemberForUpdate(projectId, userId);
        PlanSection section = requireSection(projectId, sectionId);
        List<PlanElement> contents = new ArrayList<>(
                planElementRepository.findAllByPlanContainerIdAndPlanSectionIdOrderBySortOrderAsc(
                        projectId, sectionId));
        if (form.getMode() == SectionDeletionMode.MOVE_CONTENT) {
            moveContents(projectId, section, form.getTargetSectionId(), contents);
        } else if (form.getMode() == SectionDeletionMode.DELETE_CONTENT) {
            deleteContents(contents);
        } else {
            throw new DomainValidationException("Für das Löschen des Projektbereichs ist ein gültiger Modus erforderlich.");
        }
        planSectionRepository.delete(section);
        planSectionRepository.flush();
        resequenceSections(new ArrayList<>(
                planSectionRepository.findAllByPlanContainerIdOrderBySortOrderAsc(projectId)));
    }

    private void moveContents(
            UUID projectId,
            PlanSection source,
            UUID targetSectionId,
            List<PlanElement> contents
    ) {
        if (targetSectionId == null) {
            throw new DomainValidationException("Für das Verschieben muss ein Zielbereich gewählt werden.");
        }
        if (source.getId().equals(targetSectionId)) {
            throw new DomainValidationException("Der zu löschende Projektbereich kann nicht Ziel des Verschiebens sein.");
        }
        PlanSection target = planSectionRepository.findByIdAndPlanContainerId(targetSectionId, projectId)
                .orElseThrow(() -> new DomainValidationException(
                        "Der Zielbereich muss zum selben Projekt gehören und vorhanden sein."
                ));
        List<PlanElement> targetContents = new ArrayList<>(
                planElementRepository.findAllByPlanContainerIdAndPlanSectionIdOrderBySortOrderAsc(
                        projectId, targetSectionId));
        int position = targetContents.size();
        for (PlanElement element : contents) {
            element.setPlanSection(target);
            element.setSortOrder(position++);
        }
    }

    private void deleteContents(List<PlanElement> contents) {
        contents.stream()
                .filter(Task.class::isInstance)
                .map(Task.class::cast)
                .forEach(task -> {
                    taskRepository.findSuccessors(task.getPlanContainer().getId(), task.getId())
                            .forEach(successor -> successor.removePrerequisite(task));
                    task.getPrerequisites().clear();
                });
        taskRepository.flush();
        planElementRepository.deleteAll(contents);
        planElementRepository.flush();
    }

    private PlanSection requireSection(UUID projectId, UUID sectionId) {
        return planSectionRepository.findByIdAndPlanContainerId(sectionId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Projektbereich wurde nicht gefunden."));
    }

    private void apply(PlanSection section, SectionForm form) {
        String title = form.getTitle().trim();
        String description = form.getDescription() == null || form.getDescription().isBlank()
                ? null : form.getDescription().trim();
        if (!java.util.Objects.equals(section.getTitle(), title)
                || !java.util.Objects.equals(section.getDescription(), description)) {
            section.setOrigin(section.getOrigin().modifiedByUser());
        }
        section.setTitle(title);
        section.setDescription(description);
    }

    private void resequenceSections(List<PlanSection> sections) {
        for (int index = 0; index < sections.size(); index++) {
            sections.get(index).setSortOrder(index);
        }
    }

    private void requireCurrentVersion(long actualVersion, Long submittedVersion) {
        if (submittedVersion == null) {
            throw new DomainValidationException("Die Versionsnummer des Projektbereichs fehlt.");
        }
        if (submittedVersion != actualVersion) {
            throw new ConflictException("Der Projektbereich wurde zwischenzeitlich geändert. Bitte lade die Seite neu.");
        }
    }
}
