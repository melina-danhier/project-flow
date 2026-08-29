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
        // Do not silently omit orphaned or foreign elements when projecting the draft.
        if (draft.getSections().stream().anyMatch(section -> section.getDraftPlan() != draft)
                || draft.getElements().stream().anyMatch(element -> element.getDraftPlan() != draft
                || element.getDraftSection() != null && (!draft.getSections().contains(element.getDraftSection())
                || !element.getDraftSection().getElements().contains(element)))
                || draft.getSections().stream().flatMap(section -> section.getElements().stream())
                .anyMatch(element -> !draft.getElements().contains(element))) {
            throw new DomainValidationException("Die Zuordnung der Entwurfselemente zu den Bereichen ist ungültig.");
        }
        var project = draft.getProject();
        var snapshot = workflowRepository.findByProjectId(project.getId())
                .map(workflow -> payloadCodec.readSnapshot(workflow.getConfirmedSnapshot()))
                .orElseGet(() -> new AiWizardSnapshot(project.getTitle(), project.getDescription(),
                        project.getStartDate(), project.getEndDate(), project.getCollaborationMode(),
                        project.getCategory(), project.getSubcategory(), project.getOtherProjectTypeDescription(), null, null, null));
        var validationSections = new java.util.ArrayList<GeneratedSection>();
        draft.getSections().stream().map(section -> validationSection(
                section.getId().toString(), section.getTitle(), section.getDescription(),
                section.getSortOrder() + 1, section.getElements())).forEach(validationSections::add);
        var unsectioned = draft.getElements().stream()
                .filter(element -> element.getDraftSection() == null).toList();
        if (!unsectioned.isEmpty()) {
            // The provider schema remains strict. This group exists only in memory for validating
            // user-edited drafts and is never materialized as a persisted section.
            int validationOrder = validationSections.stream().mapToInt(GeneratedSection::order).max().orElse(0) + 1;
            validationSections.add(validationSection("unsectioned-review-elements", "Ohne Bereich", null,
                    validationOrder, unsectioned));
        }
        var plan = new GeneratedPlanResponse(validationSections);
        var result = generationValidator.validatePlan(plan, snapshot);
        if (!result.isValid()) {
            throw new DomainValidationException("Der Entwurf kann noch nicht übernommen werden: "
                    + result.issues().getFirst().message());
        }
    }

    private GeneratedTask task(DraftTask task) {
        return new GeneratedTask(task.getId().toString(), task.getTitle(), task.getDescription(),
                task.getEstimatedHours(), task.getStartDate(), task.getDueDate(), task.getCriticalAssumption(),
                generatedOrigin(task), task.getSortOrder() + 1, task.getPrerequisites().stream()
                .map(prerequisite -> prerequisite.getId().toString()).toList(), task.getPriority());
    }

    private GeneratedSection validationSection(String id, String title, String description, int order,
                                               java.util.List<DraftPlanElement> elements) {
        return new GeneratedSection(id, title, description, order,
                elements.stream().filter(DraftTask.class::isInstance)
                        .map(DraftTask.class::cast).map(this::task).toList(),
                elements.stream().filter(DraftMilestone.class::isInstance)
                        .map(DraftMilestone.class::cast).map(milestone -> new GeneratedMilestone(
                                milestone.getId().toString(), milestone.getTitle(),
                                milestone.getDueDate(), milestone.getSortOrder() + 1)).toList());
    }

    private GeneratedElementOrigin generatedOrigin(DraftPlanElement element) {
        return element.getOrigin() == de.melinadanhier.projectflow.planelement.model.ElementOrigin.USER
                ? GeneratedElementOrigin.USER_INPUT : GeneratedElementOrigin.AI_INFERRED;
    }

    private void requireValidBean(Object value) {
        Object initialized = Hibernate.unproxy(value);
        var violations = beanValidator.validate(initialized);
        if (!violations.isEmpty()) {
            throw new DomainValidationException("Der Entwurf enthält ungültige Angaben. Bitte prüfe Titel, Texte und Aufwand.");
        }
    }
}
