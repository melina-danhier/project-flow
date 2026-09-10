package de.melinadanhier.projectflow.ai.validation.planchange;

import de.melinadanhier.projectflow.ai.exception.AiOutputValidationException;
import de.melinadanhier.projectflow.ai.model.improvement.AiImprovementElementType;
import de.melinadanhier.projectflow.ai.model.improvement.AiImprovementPlanContext;
import de.melinadanhier.projectflow.ai.model.planchange.*;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;

@Component
public class AiPlanChangeResponseValidator {
    private static final Set<String> SECTION_FIELDS = Set.of("title", "description", "position");
    private static final Set<String> TASK_FIELDS = Set.of(
            "title", "description", "priority", "estimatedHours", "startDate", "dueDate", "section", "position");
    private static final Set<String> MILESTONE_FIELDS = Set.of(
            "title", "description", "dueDate", "section", "position");

    public AiPlanChangeResponse validate(AiPlanChangeResponse response, AiImprovementPlanContext plan,
                                         LocalDate projectStart, LocalDate projectEnd) {
        List<String> issues = new ArrayList<>();
        if (response == null) throw invalid(List.of("PLAN_CHANGE_EMPTY | $ | Die KI-Antwort fehlt."));
        response = normalizeChanges(response);
        text(response.summary(), true, 500, "$.summary", issues);
        if (response.applicability() == null) {
            issues.add("PLAN_CHANGE_APPLICABILITY | $.applicability | Die fachliche Entscheidung fehlt.");
        }
        text(response.rejectionReason(), response.applicability() == AiPlanChangeApplicability.NOT_APPLICABLE,
                500, "$.rejectionReason", issues);
        List<AiSectionChange> sections = list(response.sections(), "$.sections", issues);
        List<AiTaskChange> tasks = list(response.tasks(), "$.tasks", issues);
        List<AiMilestoneChange> milestones = list(response.milestones(), "$.milestones", issues);
        if (response.applicability() == AiPlanChangeApplicability.NOT_APPLICABLE) {
            if (!sections.isEmpty() || !tasks.isEmpty() || !milestones.isEmpty())
                issues.add("PLAN_CHANGE_REJECTED_WITH_DIFF | $ | Eine fachliche Ablehnung darf keinen Diff enthalten.");
            if (!issues.isEmpty()) throw invalid(issues);
            return response;
        }
        if (!blank(response.rejectionReason()))
            issues.add("PLAN_CHANGE_REJECTION_REASON | $.rejectionReason | Ein anwendbarer Wunsch darf keine Ablehnung enthalten.");
        if (sections.size() + tasks.size() + milestones.size() == 0)
            issues.add("PLAN_CHANGE_EMPTY | $ | Der Änderungsvorschlag enthält keine Änderungen.");

        Map<String, AiImprovementPlanContext.Section> sectionById = new HashMap<>();
        Map<String, AiImprovementPlanContext.Element> elementById = new HashMap<>();
        Map<String, String> elementSection = new HashMap<>();
        if (plan != null && plan.sections() != null) for (var section : plan.sections()) {
            if (section.reference() != null) sectionById.put(section.reference(), section);
            if (section.elements() != null) for (var element : section.elements()) {
                elementById.put(element.reference(), element);
                elementSection.put(element.reference(), section.reference());
            }
        }
        Set<String> newSections = new HashSet<>();
        Set<String> changed = new HashSet<>();
        for (int i = 0; i < sections.size(); i++) validateSection(sections.get(i), i, sectionById, newSections, changed, issues);
        for (int i = 0; i < tasks.size(); i++) validateTask(tasks.get(i), i, sectionById, newSections,
                elementById, elementSection, changed, projectStart, projectEnd, issues);
        for (int i = 0; i < milestones.size(); i++) validateMilestone(milestones.get(i), i, sectionById, newSections,
                elementById, elementSection, changed, projectStart, projectEnd, issues);
        if (!issues.isEmpty()) throw invalid(issues);
        return response;
    }

    private AiPlanChangeResponse normalizeChanges(AiPlanChangeResponse response) {
        List<AiSectionChange> sections = response.sections() == null ? null : response.sections().stream().map(change -> {
            if (change == null) return null;
            if (change.operation() == AiPlanChangeOperation.MODIFIED) {
                List<String> fields = safe(change.changedFields());
                return new AiSectionChange(change.operation(), change.existingSectionId(), null, fields,
                        fields.contains("title") ? change.title() : null,
                        fields.contains("description") ? change.description() : null,
                        fields.contains("position") ? emptyToNull(change.beforeSectionId()) : null,
                        fields.contains("position") ? emptyToNull(change.afterSectionId()) : null,
                        change.explanation());
            }
            LinkedHashSet<String> fields = new LinkedHashSet<>(safe(change.changedFields()));
            addIf(fields, "title", !blank(change.title()));
            addIf(fields, "description", change.description() != null);
            boolean positionDeclared = fields.contains("position")
                    && exactlyOne(change.beforeSectionId(), change.afterSectionId());
            if (!positionDeclared) fields.remove("position");
            return new AiSectionChange(change.operation(), null, change.newSectionReference(),
                    List.copyOf(fields), change.title(), change.description(),
                    positionDeclared ? emptyToNull(change.beforeSectionId()) : null,
                    positionDeclared ? emptyToNull(change.afterSectionId()) : null, change.explanation());
        }).toList();
        List<AiTaskChange> tasks = response.tasks() == null ? null : response.tasks().stream().map(change -> {
            if (change == null) return null;
            if (change.operation() == AiPlanChangeOperation.MODIFIED) {
                List<String> fields = safe(change.changedFields());
                return new AiTaskChange(change.operation(), change.existingTaskId(),
                        fields.contains("section") ? emptyToNull(change.targetSectionId()) : null, fields,
                        fields.contains("title") ? change.title() : null,
                        fields.contains("description") ? change.description() : null,
                        fields.contains("priority") ? change.priority() : null,
                        fields.contains("estimatedHours") ? change.estimatedHours() : null,
                        fields.contains("startDate") ? change.startDate() : null,
                        fields.contains("dueDate") ? change.dueDate() : null,
                        fields.contains("position") ? normalizePlacement(change.placement()) : new AiRelativePlacement(null, null),
                        change.explanation());
            }
            LinkedHashSet<String> fields = new LinkedHashSet<>(safe(change.changedFields()));
            addIf(fields, "title", !blank(change.title())); addIf(fields, "description", change.description() != null);
            addIf(fields, "priority", change.priority() != null); addIf(fields, "estimatedHours", change.estimatedHours() != null);
            addIf(fields, "startDate", change.startDate() != null); addIf(fields, "dueDate", change.dueDate() != null);
            addIf(fields, "section", !blank(change.targetSectionId()));
            boolean positionDeclared = fields.contains("position") && change.placement() != null
                    && exactlyOne(change.placement().beforeElementId(), change.placement().afterElementId());
            if (!positionDeclared) fields.remove("position");
            return new AiTaskChange(change.operation(), null, change.targetSectionId(),
                    List.copyOf(fields), change.title(), change.description(), change.priority(), change.estimatedHours(),
                    change.startDate(), change.dueDate(),
                    positionDeclared ? normalizePlacement(change.placement()) : new AiRelativePlacement(null, null),
                    change.explanation());
        }).toList();
        List<AiMilestoneChange> milestones = response.milestones() == null ? null : response.milestones().stream().map(change -> {
            if (change == null) return null;
            if (change.operation() == AiPlanChangeOperation.MODIFIED) {
                List<String> fields = safe(change.changedFields());
                return new AiMilestoneChange(change.operation(), change.existingMilestoneId(),
                        fields.contains("section") ? emptyToNull(change.targetSectionId()) : null, fields,
                        fields.contains("title") ? change.title() : null,
                        fields.contains("description") ? change.description() : null,
                        fields.contains("dueDate") ? change.dueDate() : null,
                        fields.contains("position") ? normalizePlacement(change.placement()) : new AiRelativePlacement(null, null),
                        change.explanation());
            }
            LinkedHashSet<String> fields = new LinkedHashSet<>(safe(change.changedFields()));
            addIf(fields, "title", !blank(change.title())); addIf(fields, "description", change.description() != null);
            addIf(fields, "dueDate", change.dueDate() != null); addIf(fields, "section", !blank(change.targetSectionId()));
            boolean positionDeclared = fields.contains("position") && change.placement() != null
                    && exactlyOne(change.placement().beforeElementId(), change.placement().afterElementId());
            if (!positionDeclared) fields.remove("position");
            return new AiMilestoneChange(change.operation(), null, change.targetSectionId(),
                    List.copyOf(fields), change.title(), change.description(), change.dueDate(),
                    positionDeclared ? normalizePlacement(change.placement()) : new AiRelativePlacement(null, null),
                    change.explanation());
        }).toList();
        return new AiPlanChangeResponse(response.applicability(), response.rejectionReason(), response.summary(),
                sections, tasks, milestones);
    }

    private void addIf(Set<String> fields, String field, boolean condition) {
        if (condition) fields.add(field);
    }

    private AiRelativePlacement normalizePlacement(AiRelativePlacement placement) {
        return placement == null ? new AiRelativePlacement(null, null)
                : new AiRelativePlacement(emptyToNull(placement.beforeElementId()), emptyToNull(placement.afterElementId()));
    }

    private String emptyToNull(String value) {
        return blank(value) ? null : value;
    }

    private boolean exactlyOne(String first, String second) {
        return blank(first) != blank(second);
    }

    private void validateSection(AiSectionChange change, int i,
                                 Map<String, AiImprovementPlanContext.Section> sections, Set<String> newSections,
                                 Set<String> changed, List<String> issues) {
        String path = "$.sections[" + i + "]";
        if (change == null || change.operation() == null) { issues.add("PLAN_CHANGE_SECTION | " + path + " | Änderung fehlt."); return; }
        fields(change.changedFields(), SECTION_FIELDS, path, issues);
        rejectUnlisted(change.changedFields(), path, issues,
                nullableMap("title", change.title(), "description", change.description(),
                        "position", !blank(change.beforeSectionId()) || !blank(change.afterSectionId()) ? Boolean.TRUE : null));
        text(change.title(), change.operation() == AiPlanChangeOperation.NEW, 100, path + ".title", issues);
        text(change.description(), false, 2000, path + ".description", issues);
        text(change.explanation(), false, 500, path + ".explanation", issues);
        if (!blank(change.beforeSectionId()) && !blank(change.afterSectionId()))
            issues.add("PLAN_CHANGE_POSITION | " + path + " | Nur eine relative Referenz ist erlaubt.");
        if (change.operation() == AiPlanChangeOperation.NEW) {
            if (!blank(change.existingSectionId()) || blank(change.newSectionReference()) || !newSections.add(change.newSectionReference()))
                issues.add("PLAN_CHANGE_NEW_SECTION | " + path + " | Neue Section-Referenz ist ungültig oder doppelt.");
            if (!safe(change.changedFields()).contains("title")) issues.add("PLAN_CHANGE_NEW_SECTION | " + path + " | Titel fehlt in changedFields.");
            changed.add("new-section:" + change.newSectionReference());
        } else {
            if (!sections.containsKey(change.existingSectionId()) || change.newSectionReference() != null)
                issues.add("PLAN_CHANGE_SECTION_ID | " + path + " | Bestehende Section-ID ist ungültig.");
            else if (!changed.add("section:" + change.existingSectionId()))
                issues.add("PLAN_CHANGE_DUPLICATE | " + path + " | Section kommt mehrfach vor.");
            else if (!sectionActuallyChanges(change, sections.get(change.existingSectionId())))
                issues.add("PLAN_CHANGE_UNCHANGED | " + path + " | Section enthält keine tatsächliche Änderung.");
        }
        sectionRef(change.beforeSectionId(), sections, change.existingSectionId(), path, issues);
        sectionRef(change.afterSectionId(), sections, change.existingSectionId(), path, issues);
    }

    private void validateTask(AiTaskChange change, int i, Map<String, AiImprovementPlanContext.Section> sections,
                              Set<String> newSections, Map<String, AiImprovementPlanContext.Element> elements,
                              Map<String, String> elementSection, Set<String> changed, LocalDate start, LocalDate end,
                              List<String> issues) {
        String path = "$.tasks[" + i + "]";
        if (change == null || change.operation() == null) { issues.add("PLAN_CHANGE_TASK | " + path + " | Änderung fehlt."); return; }
        fields(change.changedFields(), TASK_FIELDS, path, issues);
        rejectUnlisted(change.changedFields(), path, issues, nullableMap(
                "title", change.title(), "description", change.description(), "priority", change.priority(),
                "estimatedHours", change.estimatedHours(), "startDate", change.startDate(), "dueDate", change.dueDate(),
                "position", change.placement() != null && (!blank(change.placement().beforeElementId()) || !blank(change.placement().afterElementId())) ? Boolean.TRUE : null));
        text(change.title(), change.operation() == AiPlanChangeOperation.NEW, 100, path + ".title", issues);
        text(change.description(), false, 2000, path + ".description", issues);
        text(change.explanation(), false, 500, path + ".explanation", issues);
        dates(change.startDate(), change.dueDate(), start, end, path, issues);
        if (change.operation() == AiPlanChangeOperation.NEW) {
            target(change.targetSectionId(), sections, newSections, path, issues);
            if (!blank(change.existingTaskId()) || change.priority() == null)
                issues.add("PLAN_CHANGE_NEW_TASK | " + path + " | Neue Aufgabe hat ID oder keine Priorität.");
            if (!safe(change.changedFields()).containsAll(List.of("title", "priority")))
                issues.add("PLAN_CHANGE_NEW_TASK | " + path + " | Pflichtfelder fehlen in changedFields.");
        } else {
            var original = elements.get(change.existingTaskId());
            if (original == null || original.elementType() != AiImprovementElementType.TASK)
                issues.add("PLAN_CHANGE_TASK_ID | " + path + " | Aufgaben-ID ist ungültig oder vom falschen Typ.");
            else if (!changed.add("element:" + change.existingTaskId()))
                issues.add("PLAN_CHANGE_DUPLICATE | " + path + " | Element kommt mehrfach vor.");
            else if (!taskActuallyChanges(change, original, elementSection.get(change.existingTaskId())))
                issues.add("PLAN_CHANGE_UNCHANGED | " + path + " | Aufgabe enthält keine tatsächliche Änderung.");
            if (safe(change.changedFields()).contains("section")) {
                target(change.targetSectionId(), sections, newSections, path, issues);
            } else if (change.targetSectionId() != null
                    && !Objects.equals(change.targetSectionId(), elementSection.get(change.existingTaskId()))) {
                issues.add("PLAN_CHANGE_SECTION | " + path + " | Ziel-Section wurde ohne Feldfreigabe geändert.");
            }
        }
        String effectiveTarget = change.targetSectionId() != null ? change.targetSectionId()
                : elementSection.get(change.existingTaskId());
        placement(change.placement(), change.existingTaskId(), effectiveTarget, sections, newSections,
                elements, elementSection, path, issues);
    }

    private void validateMilestone(AiMilestoneChange change, int i, Map<String, AiImprovementPlanContext.Section> sections,
                                   Set<String> newSections, Map<String, AiImprovementPlanContext.Element> elements,
                                   Map<String, String> elementSection, Set<String> changed, LocalDate start, LocalDate end,
                                   List<String> issues) {
        String path = "$.milestones[" + i + "]";
        if (change == null || change.operation() == null) { issues.add("PLAN_CHANGE_MILESTONE | " + path + " | Änderung fehlt."); return; }
        fields(change.changedFields(), MILESTONE_FIELDS, path, issues);
        rejectUnlisted(change.changedFields(), path, issues, nullableMap(
                "title", change.title(), "description", change.description(), "dueDate", change.dueDate(),
                "position", change.placement() != null && (!blank(change.placement().beforeElementId()) || !blank(change.placement().afterElementId())) ? Boolean.TRUE : null));
        text(change.title(), change.operation() == AiPlanChangeOperation.NEW, 100, path + ".title", issues);
        text(change.description(), false, 2000, path + ".description", issues);
        text(change.explanation(), false, 500, path + ".explanation", issues);
        dates(null, change.dueDate(), start, end, path, issues);
        if (change.operation() == AiPlanChangeOperation.NEW) {
            target(change.targetSectionId(), sections, newSections, path, issues);
            if (!blank(change.existingMilestoneId()))
                issues.add("PLAN_CHANGE_NEW_MILESTONE | " + path + " | Neuer Meilenstein darf keine ID haben.");
            if (!safe(change.changedFields()).contains("title"))
                issues.add("PLAN_CHANGE_NEW_MILESTONE | " + path + " | Titel fehlt in changedFields.");
        } else {
            var original = elements.get(change.existingMilestoneId());
            if (original == null || original.elementType() != AiImprovementElementType.MILESTONE)
                issues.add("PLAN_CHANGE_MILESTONE_ID | " + path + " | Meilenstein-ID ist ungültig oder vom falschen Typ.");
            else if (!changed.add("element:" + change.existingMilestoneId()))
                issues.add("PLAN_CHANGE_DUPLICATE | " + path + " | Element kommt mehrfach vor.");
            else if (!milestoneActuallyChanges(change, original, elementSection.get(change.existingMilestoneId())))
                issues.add("PLAN_CHANGE_UNCHANGED | " + path + " | Meilenstein enthält keine tatsächliche Änderung.");
            if (safe(change.changedFields()).contains("section")) {
                target(change.targetSectionId(), sections, newSections, path, issues);
            } else if (change.targetSectionId() != null
                    && !Objects.equals(change.targetSectionId(), elementSection.get(change.existingMilestoneId()))) {
                issues.add("PLAN_CHANGE_SECTION | " + path + " | Ziel-Section wurde ohne Feldfreigabe geändert.");
            }
        }
        String effectiveTarget = change.targetSectionId() != null ? change.targetSectionId()
                : elementSection.get(change.existingMilestoneId());
        placement(change.placement(), change.existingMilestoneId(), effectiveTarget, sections, newSections,
                elements, elementSection, path, issues);
    }

    private void fields(List<String> fields, Set<String> allowed, String path, List<String> issues) {
        if (fields == null || fields.isEmpty() || fields.stream().anyMatch(Objects::isNull)
                || !allowed.containsAll(fields) || new HashSet<>(fields).size() != fields.size())
            issues.add("PLAN_CHANGE_FIELDS | " + path + ".changedFields | Feldfreigabe ist ungültig.");
    }
    private void rejectUnlisted(List<String> fields, String path, List<String> issues, Map<String, Object> values) {
        for (var entry : values.entrySet()) if (entry.getValue() != null && !safe(fields).contains(entry.getKey()))
            issues.add("PLAN_CHANGE_UNLISTED_FIELD | " + path + "." + entry.getKey() + " | Wert ist nicht in changedFields freigegeben.");
    }
    private Map<String, Object> nullableMap(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
        return result;
    }
    private boolean sectionActuallyChanges(AiSectionChange c, AiImprovementPlanContext.Section old) {
        return safe(c.changedFields()).stream().anyMatch(field -> switch (field) {
            case "title" -> !Objects.equals(trim(c.title()), old.title());
            case "description" -> !Objects.equals(trim(c.description()), old.description());
            case "position" -> !blank(c.beforeSectionId()) || !blank(c.afterSectionId());
            default -> false;
        });
    }
    private boolean taskActuallyChanges(AiTaskChange c, AiImprovementPlanContext.Element old, String oldSection) {
        return safe(c.changedFields()).stream().anyMatch(field -> switch (field) {
            case "title" -> !Objects.equals(trim(c.title()), old.title());
            case "description" -> !Objects.equals(trim(c.description()), old.description());
            case "priority" -> !Objects.equals(c.priority(), old.priority());
            case "estimatedHours" -> !Objects.equals(c.estimatedHours(), old.estimatedHours());
            case "startDate" -> !Objects.equals(c.startDate(), old.startDate());
            case "dueDate" -> !Objects.equals(c.dueDate(), old.dueDate());
            case "section" -> !Objects.equals(c.targetSectionId(), oldSection);
            case "position" -> !blank(c.placement().beforeElementId()) || !blank(c.placement().afterElementId());
            default -> false;
        });
    }
    private boolean milestoneActuallyChanges(AiMilestoneChange c, AiImprovementPlanContext.Element old, String oldSection) {
        return safe(c.changedFields()).stream().anyMatch(field -> switch (field) {
            case "title" -> !Objects.equals(trim(c.title()), old.title());
            case "description" -> !Objects.equals(trim(c.description()), old.description());
            case "dueDate" -> !Objects.equals(c.dueDate(), old.dueDate());
            case "section" -> !Objects.equals(c.targetSectionId(), oldSection);
            case "position" -> !blank(c.placement().beforeElementId()) || !blank(c.placement().afterElementId());
            default -> false;
        });
    }
    private String trim(String value) { return value == null || value.trim().isEmpty() ? null : value.trim(); }
    private void target(String target, Map<String, ?> sections, Set<String> newSections, String path, List<String> issues) {
        if (blank(target) || (!sections.containsKey(target) && !newSections.contains(target)))
            issues.add("PLAN_CHANGE_TARGET_SECTION | " + path + ".targetSectionId | Ziel-Section ist ungültig.");
    }
    private void placement(AiRelativePlacement placement, String self, String target, Map<String, ?> sections,
                           Set<String> newSections, Map<String, ?> elements, Map<String, String> elementSection,
                           String path, List<String> issues) {
        if (placement == null) { issues.add("PLAN_CHANGE_POSITION | " + path + ".placement | Platzierung fehlt."); return; }
        String before = blank(placement.beforeElementId()) ? null : placement.beforeElementId();
        String after = blank(placement.afterElementId()) ? null : placement.afterElementId();
        String ref = before != null ? before : after;
        if (before != null && after != null)
            issues.add("PLAN_CHANGE_POSITION | " + path + ".placement | Nur before oder after ist erlaubt.");
        if (ref != null && (ref.equals(self) || !elements.containsKey(ref) || newSections.contains(target)
                || !Objects.equals(elementSection.get(ref), target)))
            issues.add("PLAN_CHANGE_POSITION_REF | " + path + ".placement | Positionsreferenz ist ungültig.");
    }
    private void sectionRef(String ref, Map<String, ?> sections, String self, String path, List<String> issues) {
        if (!blank(ref) && (ref.equals(self) || !sections.containsKey(ref)))
            issues.add("PLAN_CHANGE_SECTION_POSITION | " + path + " | Section-Referenz ist ungültig.");
    }
    private void dates(LocalDate from, LocalDate due, LocalDate start, LocalDate end, String path, List<String> issues) {
        if (from != null && due != null && due.isBefore(from)) issues.add("PLAN_CHANGE_DATES | " + path + " | Datumsbereich ist ungültig.");
        if ((start != null && ((from != null && from.isBefore(start)) || (due != null && due.isBefore(start))))
                || (end != null && ((from != null && from.isAfter(end)) || (due != null && due.isAfter(end)))))
            issues.add("PLAN_CHANGE_PROJECT_DATES | " + path + " | Datum liegt außerhalb des Projektzeitraums.");
    }
    private void text(String value, boolean required, int max, String path, List<String> issues) {
        if ((required && blank(value)) || (value != null && value.trim().length() > max))
            issues.add("PLAN_CHANGE_TEXT | " + path + " | Text fehlt oder ist zu lang.");
    }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private <T> List<T> safe(List<T> value) { return value == null ? List.of() : value; }
    private <T> List<T> list(List<T> value, String path, List<String> issues) {
        if (value == null) { issues.add("PLAN_CHANGE_LIST | " + path + " | Liste fehlt."); return List.of(); }
        return value;
    }
    private AiOutputValidationException invalid(List<String> issues) {
        return new AiOutputValidationException("Der KI-Änderungsvorschlag ist ungültig.", issues);
    }
}
