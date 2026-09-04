package de.melinadanhier.projectflow.draft.service;

import de.melinadanhier.projectflow.ai.model.generation.*;
import de.melinadanhier.projectflow.ai.validation.generation.GenerationResponseValidator;
import de.melinadanhier.projectflow.common.exception.DomainValidationException;
import de.melinadanhier.projectflow.draft.model.*;
import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;
import de.melinadanhier.projectflow.generation.persistence.AiWorkflowPayloadCodec;
import de.melinadanhier.projectflow.generation.repository.AiPlanGenerationWorkflowRepository;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.hibernate.Hibernate;

import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DraftValidationService {
    private final GenerationResponseValidator generationValidator;
    private final Validator beanValidator;
    private final AiPlanGenerationWorkflowRepository workflowRepository;
    private final AiWorkflowPayloadCodec payloadCodec;

    public void validate(DraftPlan draft) {
        requireValidBean(draft);
        draft.getSections().forEach(this::requireValidBean);
        draft.getElements().forEach(this::requireValidBean);
        requireConsistentGraph(draft);
        validateProjectedPlan(draft, draft.getSections(), draft.getElements());
    }

    public void validateForApplication(DraftPlan draft) {
        requireValidBean(draft);
        var sections = draft.getSections().stream().filter(this::included).toList();
        var elements = draft.getElements().stream().filter(this::included).toList();
        sections.forEach(this::requireValidBean);
        elements.forEach(this::requireValidBean);
        requireConsistentGraph(draft);
        validateApplicationDatesAndDependencies(draft, elements);
    }

    private void requireConsistentGraph(DraftPlan draft) {
        // Do not silently omit orphaned or foreign elements when projecting the draft.
        if (draft.getSections().stream().anyMatch(section -> !sameEntity(section.getDraftPlan(), draft))
                || draft.getElements().stream().anyMatch(element -> !sameEntity(element.getDraftPlan(), draft)
                || element.getDraftSection() != null && (!containsEntity(draft.getSections(), element.getDraftSection())
                || !containsEntity(element.getDraftSection().getElements(), element)))
                || draft.getSections().stream().flatMap(section -> section.getElements().stream())
                .anyMatch(element -> !containsEntity(draft.getElements(), element))) {
            throw new DomainValidationException("Die Zuordnung der Entwurfselemente zu den Bereichen ist ungültig.");
        }
        var draftTasks = draft.getElements().stream()
                .filter(DraftTask.class::isInstance)
                .map(DraftTask.class::cast)
                .toList();
        boolean invalidDependency = draftTasks.stream()
                .flatMap(task -> task.getPrerequisites().stream())
                .anyMatch(prerequisite -> prerequisite == null
                        || !sameEntity(prerequisite.getDraftPlan(), draft)
                        || draftTasks.stream().noneMatch(candidate -> sameEntity(candidate, prerequisite)));
        if (invalidDependency) {
            throw new DomainValidationException(
                    "Eine Aufgabenabhängigkeit verweist auf eine Aufgabe außerhalb dieses Entwurfs.");
        }
    }

    private void validateProjectedPlan(DraftPlan draft, java.util.List<DraftSection> includedSections,
                                       java.util.List<DraftPlanElement> includedElements) {
        var project = draft.getProject();
        var snapshot = workflowRepository.findByProjectId(project.getId())
                .map(workflow -> payloadCodec.readSnapshot(workflow.getConfirmedSnapshot()))
                .orElseGet(() -> new AiWizardSnapshot(project.getTitle(), project.getDescription(),
                        project.getStartDate(), project.getEndDate(), project.getCollaborationMode(),
                        project.getCategory(), project.getSubcategory(), project.getOtherProjectTypeDescription(), null, null, null));
        var validationSections = new java.util.ArrayList<GeneratedSection>();
        Set<DraftPlanElement> included = new HashSet<>(includedElements);
        includedSections.stream().map(section -> validationSection(
                section.getId().toString(), section.getTitle(), section.getDescription(),
                section.getSortOrder() + 1, section.getElements().stream().filter(included::contains).toList(),
                included)).forEach(validationSections::add);
        var unsectioned = includedElements.stream()
                .filter(element -> element.getDraftSection() == null
                        || !includedSections.contains(element.getDraftSection())).toList();
        if (!unsectioned.isEmpty()) {
            // The provider schema remains strict. This group exists only in memory for validating
            // user-edited drafts and is never materialized as a persisted section.
            int validationOrder = validationSections.stream().mapToInt(GeneratedSection::order).max().orElse(0) + 1;
            validationSections.add(validationSection("unsectioned-review-elements", "Ohne Bereich", null,
                    validationOrder, unsectioned, included));
        }
        var plan = new GeneratedPlanResponse(validationSections, java.util.List.of());
        var result = generationValidator.validatePlan(plan, snapshot);
        if (!result.isValid()) {
            throw new DomainValidationException("Der Entwurf kann noch nicht übernommen werden: "
                    + result.issues().getFirst().message());
        }
    }

    private GeneratedTask task(DraftTask task, Set<DraftPlanElement> included) {
        return new GeneratedTask(task.getId().toString(), task.getTitle(), task.getDescription(),
                task.getEstimatedHours(), task.getStartDate(), task.getDueDate(),
                generatedOrigin(task), task.getSortOrder() + 1, task.getPrerequisites().stream()
                .filter(included::contains)
                .map(prerequisite -> prerequisite.getId().toString()).toList(), task.getPriority());
    }

    private GeneratedSection validationSection(String id, String title, String description, int order,
                                               java.util.List<DraftPlanElement> elements,
                                               Set<DraftPlanElement> included) {
        return new GeneratedSection(id, title, description, order,
                elements.stream().filter(DraftTask.class::isInstance)
                        .map(DraftTask.class::cast).map(task -> task(task, included)).toList(),
                elements.stream().filter(DraftMilestone.class::isInstance)
                        .map(DraftMilestone.class::cast).map(milestone -> new GeneratedMilestone(
                                milestone.getId().toString(), milestone.getTitle(),
                                milestone.getDueDate(), milestone.getSortOrder() + 1)).toList());
    }

    private boolean included(DraftSection section) {
        return section.getReviewStatus() != DraftReviewStatus.REJECTED;
    }

    private boolean included(DraftPlanElement element) {
        return element.getReviewStatus() != DraftReviewStatus.REJECTED;
    }

    private void validateApplicationDatesAndDependencies(
            DraftPlan draft, java.util.List<DraftPlanElement> includedElements) {
        var included = new HashSet<>(includedElements);
        var project = draft.getProject();
        for (DraftPlanElement element : includedElements) {
            java.time.LocalDate date = element instanceof DraftTask task ? task.getDueDate()
                    : ((DraftMilestone) element).getDueDate();
            if (element instanceof DraftTask task && task.getStartDate() != null && task.getDueDate() != null
                    && task.getStartDate().isAfter(task.getDueDate())) {
                throw new DomainValidationException("Eine übernommene Aufgabe beginnt nach ihrer Deadline.");
            }
            if (date != null && (project.getStartDate() != null && date.isBefore(project.getStartDate())
                    || project.getEndDate() != null && date.isAfter(project.getEndDate()))) {
                throw new DomainValidationException("Ein übernommener Termin liegt außerhalb des Projektzeitraums.");
            }
            if (element instanceof DraftTask task && task.getStartDate() != null
                    && (project.getStartDate() != null && task.getStartDate().isBefore(project.getStartDate())
                    || project.getEndDate() != null && task.getStartDate().isAfter(project.getEndDate()))) {
                throw new DomainValidationException("Ein übernommener Aufgabenstart liegt außerhalb des Projektzeitraums.");
            }
        }
        var tasks = includedElements.stream().filter(DraftTask.class::isInstance)
                .map(DraftTask.class::cast).toList();
        for (DraftTask task : tasks) {
            if (task.getPrerequisites().contains(task)) {
                throw new DomainValidationException("Eine Aufgabe darf nicht von sich selbst abhängen.");
            }
        }
        var completed = new HashSet<DraftTask>();
        var active = new HashSet<DraftTask>();
        for (DraftTask task : tasks) {
            if (hasCycle(task, included, completed, active)) {
                throw new DomainValidationException("Die übernommenen Aufgabenabhängigkeiten enthalten einen Zyklus.");
            }
        }
    }

    private boolean hasCycle(DraftTask task, Set<DraftPlanElement> included,
                             Set<DraftTask> completed, Set<DraftTask> active) {
        if (active.contains(task)) return true;
        if (completed.contains(task)) return false;
        active.add(task);
        for (DraftTask prerequisite : task.getPrerequisites()) {
            if (included.contains(prerequisite)
                    && hasCycle(prerequisite, included, completed, active)) return true;
        }
        active.remove(task);
        completed.add(task);
        return false;
    }

    private GeneratedElementOrigin generatedOrigin(DraftPlanElement element) {
        return element.getOrigin() == de.melinadanhier.projectflow.planelement.model.ElementOrigin.USER
                ? GeneratedElementOrigin.USER_INPUT : GeneratedElementOrigin.AI_INFERRED;
    }

    private boolean sameEntity(de.melinadanhier.projectflow.common.model.MutableEntity left,
                               de.melinadanhier.projectflow.common.model.MutableEntity right) {
        if (left == right) return true;
        return left != null && right != null && left.getId() != null && left.getId().equals(right.getId());
    }

    private boolean containsEntity(java.util.Collection<? extends de.melinadanhier.projectflow.common.model.MutableEntity> values,
                                   de.melinadanhier.projectflow.common.model.MutableEntity expected) {
        return values.stream().anyMatch(value -> sameEntity(value, expected));
    }

    private void requireValidBean(Object value) {
        Object initialized = Hibernate.unproxy(value);
        var violations = beanValidator.validate(initialized);
        if (!violations.isEmpty()) {
            throw new DomainValidationException("Der Entwurf enthält ungültige Angaben. Bitte prüfe Titel, Texte und Aufwand.");
        }
    }
}
