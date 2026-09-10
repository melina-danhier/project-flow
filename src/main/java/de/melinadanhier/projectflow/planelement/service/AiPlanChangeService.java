package de.melinadanhier.projectflow.planelement.service;

import de.melinadanhier.projectflow.ai.model.improvement.*;
import de.melinadanhier.projectflow.ai.model.planchange.*;
import de.melinadanhier.projectflow.ai.provider.AiClient;
import de.melinadanhier.projectflow.ai.validation.planchange.AiPlanChangeResponseValidator;
import de.melinadanhier.projectflow.common.exception.DomainValidationException;
import de.melinadanhier.projectflow.plancontainer.project.model.Project;
import de.melinadanhier.projectflow.plancontainer.project.service.ProjectAuthorizationService;
import de.melinadanhier.projectflow.planelement.dto.planchange.*;
import de.melinadanhier.projectflow.planelement.model.*;
import de.melinadanhier.projectflow.planelement.repository.PlanElementRepository;
import de.melinadanhier.projectflow.planelement.repository.PlanSectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AiPlanChangeService {
    private final ProjectAuthorizationService authorizationService;
    private final PlanSectionRepository sectionRepository;
    private final PlanElementRepository elementRepository;
    private final AiClient aiClient;
    private final AiPlanChangeResponseValidator validator;

    @Transactional(readOnly = true)
    public void requireAccess(UUID projectId, UUID userId) { authorizationService.requireEditableMember(projectId, userId); }

    @Transactional(readOnly = true)
    public PlanChangeProposal propose(UUID projectId, PlanChangeForm form, UUID userId) {
        Project project = authorizationService.requireEditableMember(projectId, userId).getProject();
        String requestText = normalize(form == null ? null : form.getChangeRequest());
        AiImprovementPlanContext plan = planContext(project);
        var request = new AiPlanChangeRequest(requestText,
                new AiImprovementProjectContext(project.getTitle(), project.getDescription(),
                        project.getStartDate(), project.getEndDate()), plan);
        AiPlanChangeResponse changes = validator.validate(aiClient.proposePlanChanges(request), plan,
                project.getStartDate(), project.getEndDate());
        if (changes.applicability() == AiPlanChangeApplicability.NOT_APPLICABLE) {
            throw new PlanChangeNotApplicableException(
                    "Der Änderungswunsch passt nicht zum aktuellen Projektplan.");
        }
        return new PlanChangeProposal(UUID.randomUUID(), projectId, project.getTitle(), requestText,
                Instant.now(), plan, changes);
    }

    public PlanChangeReview review(PlanChangeProposal proposal) {
        Map<String, AiImprovementPlanContext.Section> sections = new LinkedHashMap<>();
        Map<String, AiImprovementPlanContext.Element> elements = new HashMap<>();
        Map<String, String> elementSections = new HashMap<>();
        for (var section : proposal.originalPlan().sections()) {
            if (section.reference() != null) sections.put(section.reference(), section);
            for (var element : section.elements()) { elements.put(element.reference(), element); elementSections.put(element.reference(), section.reference()); }
        }
        Map<String, ReviewBuilder> affected = new LinkedHashMap<>();
        for (AiSectionChange change : proposal.changes().sections()) {
            String key = change.operation() == AiPlanChangeOperation.NEW ? change.newSectionReference() : change.existingSectionId();
            var old = sections.get(change.existingSectionId());
            String title = change.changedFields().contains("title") ? change.title() : old == null ? change.title() : old.title();
            ReviewBuilder builder = affected.computeIfAbsent(key, ignored -> new ReviewBuilder(title));
            builder.operation = change.operation(); builder.sectionChanged = true;
            add(builder.fields, "Titel", old == null ? null : old.title(), change.title(), change.changedFields(), "title");
            add(builder.fields, "Beschreibung", old == null ? null : old.description(), change.description(), change.changedFields(), "description");
            if (change.changedFields().contains("position")) builder.fields.add(new PlanChangeReview.FieldChange(
                    "Position", old == null ? null : "Bisherige Position", sectionPosition(change, sections)));
        }
        for (AiTaskChange change : proposal.changes().tasks()) {
            var old = elements.get(change.existingTaskId());
            String sectionKey = change.targetSectionId() != null ? change.targetSectionId()
                    : elementSections.get(change.existingTaskId());
            ReviewBuilder builder = affected.computeIfAbsent(sectionKey, key -> new ReviewBuilder(sectionTitle(key, sections, proposal.changes())));
            List<PlanChangeReview.FieldChange> fields = new ArrayList<>();
            add(fields, "Titel", old == null ? null : old.title(), change.title(), change.changedFields(), "title");
            add(fields, "Beschreibung", old == null ? null : old.description(), change.description(), change.changedFields(), "description");
            add(fields, "Priorität", old == null ? null : value(old.priority()), value(change.priority()), change.changedFields(), "priority");
            add(fields, "Aufwand", old == null ? null : hours(old.estimatedHours()), hours(change.estimatedHours()), change.changedFields(), "estimatedHours");
            add(fields, "Start", old == null ? null : date(old.startDate()), date(change.startDate()), change.changedFields(), "startDate");
            add(fields, "Fällig", old == null ? null : date(old.dueDate()), date(change.dueDate()), change.changedFields(), "dueDate");
            addPlacement(fields, change.changedFields(), change.placement(), change.targetSectionId(), sections, elements);
            String title = change.changedFields().contains("title") ? change.title() : old == null ? change.title() : old.title();
            builder.elements.add(new PlanChangeReview.Element("Aufgabe", title, change.operation(), fields, change.explanation()));
        }
        for (AiMilestoneChange change : proposal.changes().milestones()) {
            var old = elements.get(change.existingMilestoneId());
            String sectionKey = change.targetSectionId() != null ? change.targetSectionId()
                    : elementSections.get(change.existingMilestoneId());
            ReviewBuilder builder = affected.computeIfAbsent(sectionKey, key -> new ReviewBuilder(sectionTitle(key, sections, proposal.changes())));
            List<PlanChangeReview.FieldChange> fields = new ArrayList<>();
            add(fields, "Titel", old == null ? null : old.title(), change.title(), change.changedFields(), "title");
            add(fields, "Beschreibung", old == null ? null : old.description(), change.description(), change.changedFields(), "description");
            add(fields, "Fällig", old == null ? null : date(old.dueDate()), date(change.dueDate()), change.changedFields(), "dueDate");
            addPlacement(fields, change.changedFields(), change.placement(), change.targetSectionId(), sections, elements);
            String title = change.changedFields().contains("title") ? change.title() : old == null ? change.title() : old.title();
            builder.elements.add(new PlanChangeReview.Element("Meilenstein", title, change.operation(), fields, change.explanation()));
        }
        return new PlanChangeReview(proposal.changes().summary(), affected.values().stream().map(ReviewBuilder::build).toList());
    }

    private AiImprovementPlanContext planContext(Project project) {
        List<PlanElement> all = elementRepository.findPlanElements(project.getId());
        PlanElementCollection collection = PlanElementCollection.copyOf(all);
        List<AiImprovementPlanContext.Section> result = new ArrayList<>(); int sectionPosition = 0;
        for (PlanSection section : sectionRepository.findAllByPlanContainerIdOrderBySortOrderAsc(project.getId())) {
            result.add(context(section.getId().toString(), section.getTitle(), section.getDescription(), ++sectionPosition,
                    collection.displayInSection(section.getId(), project.getSortMode())));
        }
        List<PlanElement> unassigned = collection.displayInSection(null, project.getSortMode());
        if (!unassigned.isEmpty()) result.add(context(null, "Ohne Bereich", null, ++sectionPosition, unassigned));
        return new AiImprovementPlanContext(project.getSortMode(), null, List.copyOf(result));
    }
    private AiImprovementPlanContext.Section context(String ref, String title, String description, int position, List<PlanElement> values) {
        List<AiImprovementPlanContext.Element> elements = new ArrayList<>(); int i = 0;
        for (PlanElement element : values) {
            if (element instanceof Task task) elements.add(new AiImprovementPlanContext.Element(AiImprovementElementType.TASK,
                    task.getId().toString(), task.getTitle(), task.getDescription(), ++i, task.getPriority(), task.getEstimatedHours(),
                    task.getStatus(), task.getStartDate(), task.getDueDate(), null,
                    task.getPrerequisites().stream().map(p -> p.getId().toString()).sorted().toList()));
            else { Milestone milestone = (Milestone) element; elements.add(new AiImprovementPlanContext.Element(
                    AiImprovementElementType.MILESTONE, milestone.getId().toString(), milestone.getTitle(), milestone.getDescription(),
                    ++i, null, null, null, null, milestone.getDueDate(), milestone.isCompleted(), List.of())); }
        }
        return new AiImprovementPlanContext.Section(ref, title, description, position, List.copyOf(elements));
    }
    private String normalize(String value) {
        if (value == null || value.trim().isEmpty()) throw new DomainValidationException("Bitte beschreibe die gewünschte Änderung.");
        String result = value.trim(); if (result.length() > PlanChangeForm.MAX_LENGTH)
            throw new DomainValidationException("Der Änderungswunsch darf höchstens 1000 Zeichen lang sein.");
        return result;
    }
    private void add(List<PlanChangeReview.FieldChange> out, String label, String before, String after, List<String> fields, String key) {
        if (fields.contains(key)) out.add(new PlanChangeReview.FieldChange(label, display(before), display(after)));
    }
    private void addPlacement(List<PlanChangeReview.FieldChange> out, List<String> fields, AiRelativePlacement placement,
                              String target, Map<String, AiImprovementPlanContext.Section> sections,
                              Map<String, AiImprovementPlanContext.Element> elements) {
        if (fields.contains("section")) out.add(new PlanChangeReview.FieldChange("Bereich", null, sectionTitle(target, sections, null)));
        if (fields.contains("position")) {
            String ref = placement.beforeElementId() != null ? placement.beforeElementId() : placement.afterElementId();
            String text = ref == null ? "Am Ende" : (placement.beforeElementId() != null ? "Vor „" : "Nach „") + elements.get(ref).title() + "“";
            out.add(new PlanChangeReview.FieldChange("Reihenfolge", null, text));
        }
    }
    private String sectionTitle(String id, Map<String, AiImprovementPlanContext.Section> sections, AiPlanChangeResponse response) {
        if (id == null) return "Ohne Bereich"; if (sections.containsKey(id)) return sections.get(id).title();
        if (response != null) return response.sections().stream().filter(s -> id.equals(s.newSectionReference())).map(AiSectionChange::title).findFirst().orElse("Neuer Bereich");
        return "Neuer Bereich";
    }
    private String sectionPosition(AiSectionChange c, Map<String, AiImprovementPlanContext.Section> sections) {
        String id = c.beforeSectionId() != null ? c.beforeSectionId() : c.afterSectionId();
        if (id == null) return "Am Ende"; return (c.beforeSectionId() != null ? "Vor „" : "Nach „") + sections.get(id).title() + "“";
    }
    private String display(String s) { return s == null || s.isBlank() ? "—" : s; }
    private String value(Object o) { return o == null ? null : o.toString(); }
    private String hours(Integer v) { return v == null ? null : v + " Std."; }
    private String date(LocalDate v) { return v == null ? null : v.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")); }
    private static class ReviewBuilder {
        String title; AiPlanChangeOperation operation; boolean sectionChanged; List<PlanChangeReview.FieldChange> fields = new ArrayList<>();
        List<PlanChangeReview.Element> elements = new ArrayList<>(); ReviewBuilder(String title) { this.title = title; }
        PlanChangeReview.Section build() { return new PlanChangeReview.Section(title, operation, sectionChanged, List.copyOf(fields), List.copyOf(elements)); }
    }
}
