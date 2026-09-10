package de.melinadanhier.projectflow.planelement.service;

import de.melinadanhier.projectflow.ai.model.improvement.*;
import de.melinadanhier.projectflow.ai.model.planchange.*;
import de.melinadanhier.projectflow.ai.provider.AiClient;
import de.melinadanhier.projectflow.ai.validation.planchange.AiPlanChangeResponseValidator;
import de.melinadanhier.projectflow.common.exception.DomainValidationException;
import de.melinadanhier.projectflow.common.exception.ConflictException;
import de.melinadanhier.projectflow.common.exception.ResourceNotFoundException;
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
        Map<UUID, Long> sectionVersions = new LinkedHashMap<>();
        sectionRepository.findAllByPlanContainerIdOrderBySortOrderAsc(projectId)
                .forEach(section -> sectionVersions.put(section.getId(), section.getLockVersion()));
        Map<UUID, Long> elementVersions = new LinkedHashMap<>();
        elementRepository.findPlanElements(projectId)
                .forEach(element -> elementVersions.put(element.getId(), element.getLockVersion()));
        return new PlanChangeProposal(UUID.randomUUID(), projectId, project.getTitle(), requestText,
                Instant.now(), plan, changes, project.getLockVersion(), sectionVersions, elementVersions);
    }

    /** Applies the already generated proposal as one locked transaction. */
    @Transactional
    public void confirm(UUID projectId, PlanChangeProposal proposal, UUID userId) {
        if (proposal == null || !projectId.equals(proposal.projectId())) {
            throw new DomainValidationException("Der KI-Änderungsvorschlag ist ungültig.");
        }
        Project project = authorizationService.requireEditableMemberForUpdate(projectId, userId).getProject();
        List<PlanSection> sections = new ArrayList<>(
                sectionRepository.findAllByPlanContainerIdOrderBySortOrderAsc(projectId));
        List<PlanElement> elements = new ArrayList<>(elementRepository.findPlanElements(projectId));
        verifySnapshot(project, proposal, sections, elements);

        // Re-run AP2's allow-list, type, reference, placement and domain validation against current data.
        AiImprovementPlanContext currentPlan = planContext(project);
        try {
            validator.validate(proposal.changes(), currentPlan, project.getStartDate(), project.getEndDate());
        } catch (de.melinadanhier.projectflow.ai.exception.AiOutputValidationException exception) {
            throw new DomainValidationException("Der KI-Änderungsvorschlag ist nicht mehr gültig.");
        }

        Map<String, PlanSection> sectionByReference = new LinkedHashMap<>();
        sections.forEach(section -> sectionByReference.put(section.getId().toString(), section));
        Map<String, PlanElement> elementByReference = new LinkedHashMap<>();
        elements.forEach(element -> elementByReference.put(element.getId().toString(), element));

        for (AiSectionChange change : proposal.changes().sections()) {
            if (change.operation() == AiPlanChangeOperation.MODIFIED) {
                applySection(sectionByReference.get(change.existingSectionId()), change);
            }
        }
        for (AiSectionChange change : proposal.changes().sections()) {
            if (change.operation() == AiPlanChangeOperation.NEW) {
                PlanSection section = new PlanSection();
                section.setPlanContainer(project);
                section.setTitle(change.title().trim());
                section.setDescription(normalizeOptional(change.description()));
                section.setOrigin(ElementOrigin.AI);
                section.setSortOrder((sections.size() + 1) * PlanOrdering.GAP);
                sectionRepository.save(section);
                sections.add(section);
                sectionByReference.put(change.newSectionReference(), section);
            }
        }

        List<Task> newTasks = new ArrayList<>();
        for (AiTaskChange change : proposal.changes().tasks()) {
            if (change.operation() == AiPlanChangeOperation.MODIFIED) {
                applyTask((Task) elementByReference.get(change.existingTaskId()), change);
            } else {
                Task task = new Task();
                task.setPlanContainer(project);
                task.setTitle(change.title().trim());
                task.setDescription(normalizeOptional(change.description()));
                task.setPriority(change.priority());
                task.setEstimatedHours(change.estimatedHours());
                task.setStartDate(change.startDate());
                task.setDueDate(change.dueDate());
                task.setOrigin(ElementOrigin.AI);
                task.setSortOrder(PlanOrdering.GAP);
                elementRepository.save(task);
                elements.add(task);
                newTasks.add(task);
            }
        }
        List<Milestone> newMilestones = new ArrayList<>();
        for (AiMilestoneChange change : proposal.changes().milestones()) {
            if (change.operation() == AiPlanChangeOperation.MODIFIED) {
                applyMilestone((Milestone) elementByReference.get(change.existingMilestoneId()), change);
            } else {
                Milestone milestone = new Milestone();
                milestone.setPlanContainer(project);
                milestone.setTitle(change.title().trim());
                milestone.setDescription(normalizeOptional(change.description()));
                milestone.setDueDate(change.dueDate());
                milestone.setOrigin(ElementOrigin.AI);
                milestone.setSortOrder(PlanOrdering.GAP);
                elementRepository.save(milestone);
                elements.add(milestone);
                newMilestones.add(milestone);
            }
        }

        applySectionPlacements(proposal.changes().sections(), sections, sectionByReference);
        applyElementPlacements(proposal.changes(), elements, sectionByReference, elementByReference,
                newTasks, newMilestones);
        sectionRepository.flush();
        elementRepository.flush();
    }

    private void verifySnapshot(Project project, PlanChangeProposal proposal, List<PlanSection> sections,
                                List<PlanElement> elements) {
        Map<UUID, Long> currentSections = new HashMap<>();
        sections.forEach(value -> currentSections.put(value.getId(), value.getLockVersion()));
        Map<UUID, Long> currentElements = new HashMap<>();
        elements.forEach(value -> currentElements.put(value.getId(), value.getLockVersion()));
        if (!currentSections.keySet().containsAll(proposal.sectionVersions().keySet())
                || !currentElements.keySet().containsAll(proposal.elementVersions().keySet())) {
            throw new ResourceNotFoundException("Ein im KI-Vorschlag referenziertes Planelement wurde nicht gefunden.");
        }
        if (project.getLockVersion() != proposal.projectVersion()
                || !currentSections.equals(proposal.sectionVersions())
                || !currentElements.equals(proposal.elementVersions())) {
            throw new ConflictException("Der Projektplan wurde seit dem KI-Vorschlag geändert. Bitte erzeuge einen neuen Vorschlag.");
        }
    }

    private void applySection(PlanSection section, AiSectionChange change) {
        if (change.changedFields().contains("title")) section.setTitle(change.title().trim());
        if (change.changedFields().contains("description")) section.setDescription(normalizeOptional(change.description()));
        section.setOrigin(ElementOrigin.AI_MODIFIED);
    }

    private void applyTask(Task task, AiTaskChange change) {
        if (change.changedFields().contains("title")) task.setTitle(change.title().trim());
        if (change.changedFields().contains("description")) task.setDescription(normalizeOptional(change.description()));
        if (change.changedFields().contains("priority")) task.setPriority(change.priority());
        if (change.changedFields().contains("estimatedHours")) task.setEstimatedHours(change.estimatedHours());
        if (change.changedFields().contains("startDate")) { task.setStartDate(change.startDate()); task.setRelativeStartDay(null); }
        if (change.changedFields().contains("dueDate")) { task.setDueDate(change.dueDate()); task.setRelativeDueDay(null); }
        task.setOrigin(ElementOrigin.AI_MODIFIED);
    }

    private void applyMilestone(Milestone milestone, AiMilestoneChange change) {
        if (change.changedFields().contains("title")) milestone.setTitle(change.title().trim());
        if (change.changedFields().contains("description")) milestone.setDescription(normalizeOptional(change.description()));
        if (change.changedFields().contains("dueDate")) { milestone.setDueDate(change.dueDate()); milestone.setRelativeDueDay(null); }
        milestone.setOrigin(ElementOrigin.AI_MODIFIED);
    }

    private void applySectionPlacements(List<AiSectionChange> changes, List<PlanSection> sections,
                                        Map<String, PlanSection> byReference) {
        for (AiSectionChange change : changes) {
            PlanSection moved = change.operation() == AiPlanChangeOperation.NEW
                    ? byReference.get(change.newSectionReference()) : byReference.get(change.existingSectionId());
            if (change.operation() != AiPlanChangeOperation.NEW && !change.changedFields().contains("position")) continue;
            sections.remove(moved);
            String anchorId = change.beforeSectionId() != null ? change.beforeSectionId() : change.afterSectionId();
            int index = anchorId == null ? sections.size() : sections.indexOf(byReference.get(anchorId));
            if (change.afterSectionId() != null) index++;
            PlanOrdering.place(sections, moved, index, PlanSection::getSortOrder, PlanSection::setSortOrder);
        }
    }

    private void applyElementPlacements(AiPlanChangeResponse response, List<PlanElement> elements,
                                        Map<String, PlanSection> sections, Map<String, PlanElement> existingElements,
                                        List<Task> newTasks, List<Milestone> newMilestones) {
        record Placement(PlanElement element, String target, AiRelativePlacement relative) {}
        List<Placement> placements = new ArrayList<>();
        int newIndex = 0;
        for (AiTaskChange change : response.tasks()) {
            PlanElement element = change.operation() == AiPlanChangeOperation.MODIFIED
                    ? existingElements.get(change.existingTaskId()) : newTasks.get(newIndex++);
            if (change.operation() == AiPlanChangeOperation.NEW || change.changedFields().contains("section") || change.changedFields().contains("position"))
                placements.add(new Placement(element, effectiveTarget(change, element), change.placement()));
        }
        int milestoneIndex = 0;
        for (AiMilestoneChange change : response.milestones()) {
            PlanElement element = change.operation() == AiPlanChangeOperation.MODIFIED
                    ? existingElements.get(change.existingMilestoneId()) : newMilestones.get(milestoneIndex++);
            if (change.operation() == AiPlanChangeOperation.NEW || change.changedFields().contains("section") || change.changedFields().contains("position"))
                placements.add(new Placement(element, effectiveTarget(change, element), change.placement()));
        }
        for (Placement placement : placements) {
            PlanSection target = placement.target() == null ? null : sections.get(placement.target());
            List<PlanElement> siblings = elements.stream().filter(candidate -> candidate != placement.element())
                    .filter(candidate -> Objects.equals(candidate.getPlanSection(), target))
                    .sorted(PlanOrdering.manual(PlanElement::getSortOrder, PlanElement::getId)).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            placement.element().setPlanSection(target);
            String before = placement.relative() == null ? null : placement.relative().beforeElementId();
            String after = placement.relative() == null ? null : placement.relative().afterElementId();
            PlanElement anchor = before != null ? existingElements.get(before) : after != null ? existingElements.get(after) : null;
            int index = anchor == null ? siblings.size() : siblings.indexOf(anchor);
            if (after != null) index++;
            PlanOrdering.place(siblings, placement.element(), index, PlanElement::getSortOrder, PlanElement::setSortOrder);
        }
    }

    private String effectiveTarget(AiTaskChange change, PlanElement element) {
        return change.changedFields().contains("section") || change.operation() == AiPlanChangeOperation.NEW
                ? change.targetSectionId() : element.getPlanSection() == null ? null : element.getPlanSection().getId().toString();
    }
    private String effectiveTarget(AiMilestoneChange change, PlanElement element) {
        return change.changedFields().contains("section") || change.operation() == AiPlanChangeOperation.NEW
                ? change.targetSectionId() : element.getPlanSection() == null ? null : element.getPlanSection().getId().toString();
    }
    private String normalizeOptional(String value) { return value == null || value.isBlank() ? null : value.trim(); }

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
                    "Reihenfolge", old == null ? null : currentSectionPosition(old, proposal.originalPlan()),
                    sectionPosition(change, sections)));
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
            addPlacement(fields, change.changedFields(), change.placement(), change.targetSectionId(),
                    elementSections.get(change.existingTaskId()), change.existingTaskId(),
                    proposal.originalPlan(), sections, elements);
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
            addPlacement(fields, change.changedFields(), change.placement(), change.targetSectionId(),
                    elementSections.get(change.existingMilestoneId()), change.existingMilestoneId(),
                    proposal.originalPlan(), sections, elements);
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
                              String target, String originalSection, String elementId,
                              AiImprovementPlanContext plan, Map<String, AiImprovementPlanContext.Section> sections,
                              Map<String, AiImprovementPlanContext.Element> elements) {
        if (fields.contains("section")) out.add(new PlanChangeReview.FieldChange("Bereich",
                sectionTitle(originalSection, sections, null), sectionTitle(target, sections, null)));
        if (fields.contains("position")) {
            String ref = placement.beforeElementId() != null ? placement.beforeElementId() : placement.afterElementId();
            String text = ref == null ? "Am Ende" : (placement.beforeElementId() != null ? "Vor „" : "Nach „") + elements.get(ref).title() + "“";
            out.add(new PlanChangeReview.FieldChange("Reihenfolge",
                    elementId == null ? null : currentElementPosition(elementId, plan), text));
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
    private String currentSectionPosition(AiImprovementPlanContext.Section selected, AiImprovementPlanContext plan) {
        List<AiImprovementPlanContext.Section> ordered = plan.sections();
        int index = ordered.indexOf(selected);
        if (index + 1 < ordered.size()) return "Vor „" + ordered.get(index + 1).title() + "“";
        if (index > 0) return "Nach „" + ordered.get(index - 1).title() + "“";
        return "Am Anfang";
    }
    private String currentElementPosition(String elementId, AiImprovementPlanContext plan) {
        for (AiImprovementPlanContext.Section section : plan.sections()) {
            List<AiImprovementPlanContext.Element> ordered = section.elements();
            for (int index = 0; index < ordered.size(); index++) {
                if (!elementId.equals(ordered.get(index).reference())) continue;
                if (index + 1 < ordered.size()) return "Vor „" + ordered.get(index + 1).title() + "“";
                if (index > 0) return "Nach „" + ordered.get(index - 1).title() + "“";
                return "Am Anfang";
            }
        }
        return "Bisherige Position";
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
