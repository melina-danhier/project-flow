package de.melinadanhier.projectflow.ai;

import de.melinadanhier.projectflow.ai.exception.AiOutputValidationException;
import de.melinadanhier.projectflow.ai.model.improvement.*;
import de.melinadanhier.projectflow.ai.model.planchange.*;
import de.melinadanhier.projectflow.ai.validation.planchange.AiPlanChangeResponseValidator;
import de.melinadanhier.projectflow.plancontainer.model.SortMode;
import de.melinadanhier.projectflow.planelement.model.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class AiPlanChangeResponseValidatorTest {
    private static final String S1 = "10000000-0000-0000-0000-000000000001";
    private static final String S2 = "10000000-0000-0000-0000-000000000002";
    private static final String T1 = "20000000-0000-0000-0000-000000000001";
    private static final String T2 = "20000000-0000-0000-0000-000000000002";
    private static final String M1 = "30000000-0000-0000-0000-000000000001";
    private final AiPlanChangeResponseValidator validator = new AiPlanChangeResponseValidator();

    @Test void acceptsNewTaskInExistingSection() { assertValid(response(List.of(), List.of(newTask(S1)), List.of())); }
    @Test void acceptsVehicleReservationCheckInNamedExistingSection() {
        var task = new AiTaskChange(AiPlanChangeOperation.NEW, null, S2,
                List.of("title", "description", "priority", "section"),
                "Fahrzeugreservierung prüfen", "Reservierung und Abholzeit verbindlich bestätigen.",
                TaskPriority.HIGH, null, null, null, place(),
                "Die Kontrolle reduziert das Risiko eines fehlenden Fahrzeugs am Umzugstag.");
        assertValid(response(List.of(), List.of(task), List.of()));
    }
    @Test void treatsBlankOptionalIdsFromProviderAsAbsent() {
        var task = new AiTaskChange(AiPlanChangeOperation.NEW, "", S2,
                List.of("title", "priority", "section"), "Fahrzeugreservierung prüfen", null,
                TaskPriority.HIGH, null, null, null, new AiRelativePlacement("", ""), null);
        assertValid(response(List.of(), List.of(task), List.of()));
    }
    @Test void derivesAllPopulatedFieldsForNewTaskWhenModelUnderReportsChangedFields() {
        var task = new AiTaskChange(AiPlanChangeOperation.NEW, null, S2,
                List.of("title", "priority"), "Fahrzeugreservierung prüfen", "Reservierung bestätigen.",
                TaskPriority.HIGH, 1, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 2),
                new AiRelativePlacement(T2, null), null);

        AiPlanChangeResponse normalized = validator.validate(response(List.of(), List.of(task), List.of()), plan(),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        assertThat(normalized.tasks().getFirst().changedFields()).containsExactly(
                "title", "priority", "description", "estimatedHours", "startDate", "dueDate", "section");
    }
    @Test void acceptsNewPhaseAndTaskDespiteProviderPlaceholderIdsAndAmbiguousPositions() {
        var section = new AiSectionChange(AiPlanChangeOperation.NEW, "provider-placeholder", "new-aftercare",
                List.of("title", "position"), "Nachbereitung", null, S1, S2, null);
        var task = new AiTaskChange(AiPlanChangeOperation.NEW, "provider-placeholder", "new-aftercare",
                List.of("title", "priority", "section", "position"), "Adresse ummelden", null,
                TaskPriority.MEDIUM, null, null, null, new AiRelativePlacement(T1, T2), null);

        AiPlanChangeResponse normalized = validator.validate(response(List.of(section), List.of(task), List.of()),
                plan(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        assertThat(normalized.sections().getFirst().existingSectionId()).isNull();
        assertThat(normalized.sections().getFirst().changedFields()).doesNotContain("position");
        assertThat(normalized.tasks().getFirst().existingTaskId()).isNull();
        assertThat(normalized.tasks().getFirst().targetSectionId()).isEqualTo("new-aftercare");
        assertThat(normalized.tasks().getFirst().changedFields()).doesNotContain("position");
    }
    @Test void acceptsModifiedTaskWithOmittedUnchangedTargetSection() {
        assertValid(response(List.of(), List.of(new AiTaskChange(AiPlanChangeOperation.MODIFIED, T1, null,
                List.of("description"), null, "Neue Beschreibung", null, null, null, null, place(), null)), List.of()));
    }
    @Test void ignoresStructuredOutputValuesNotDeclaredForDescriptionOnlyModification() {
        var task = new AiTaskChange(AiPlanChangeOperation.MODIFIED, T1, S2,
                List.of("description"), "Vom Modell wiederholter Titel", "Genauer Beschreibungstext",
                TaskPriority.HIGH, 99, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 2),
                new AiRelativePlacement("erfundene-position", null), null);

        AiPlanChangeResponse normalized = validator.validate(response(List.of(), List.of(task), List.of()), plan(),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        AiTaskChange result = normalized.tasks().getFirst();
        assertThat(result.description()).isEqualTo("Genauer Beschreibungstext");
        assertThat(result.title()).isNull();
        assertThat(result.priority()).isNull();
        assertThat(result.estimatedHours()).isNull();
        assertThat(result.startDate()).isNull();
        assertThat(result.dueDate()).isNull();
        assertThat(result.targetSectionId()).isNull();
        assertThat(result.placement()).isEqualTo(new AiRelativePlacement(null, null));
    }
    @Test void acceptsModifiedTask() { assertValid(response(List.of(), List.of(modifiedTask("Neuer Titel", S1)), List.of())); }
    @Test void acceptsNewAndModifiedMilestones() {
        assertValid(response(List.of(), List.of(), List.of(
                new AiMilestoneChange(AiPlanChangeOperation.NEW, null, S1, List.of("title"), "Neu", null, null, place(), null),
                new AiMilestoneChange(AiPlanChangeOperation.MODIFIED, M1, S1, List.of("dueDate"), null, null,
                        LocalDate.of(2026, 2, 10), place(), null))));
    }
    @Test void acceptsNewSectionWithNewElement() {
        var section = new AiSectionChange(AiPlanChangeOperation.NEW, null, "new-1", List.of("title"), "Neu", null, null, null, null);
        assertValid(response(List.of(section), List.of(newTask("new-1")), List.of()));
    }
    @Test void acceptsChangedSectionAndSeveralAffectedSections() {
        var section = new AiSectionChange(AiPlanChangeOperation.MODIFIED, S1, null, List.of("description"), null, "Neu", null, null, null);
        assertValid(response(List.of(section), List.of(modifiedTaskWithId(T2, S2, "Neu")), List.of()));
    }
    @Test void rejectsUnknownOrForeignId() { assertInvalid(response(List.of(), List.of(modifiedTaskWithId("missing", S1)), List.of())); }
    @Test void rejectsWrongElementType() { assertInvalid(response(List.of(), List.of(modifiedTaskWithId(M1, S1)), List.of())); }
    @Test void rejectsInvalidTargetSection() { assertInvalid(response(List.of(), List.of(newTask("foreign")), List.of())); }
    @Test void rejectsInvalidRelativeReferenceAndSelfReference() {
        assertInvalid(response(List.of(), List.of(new AiTaskChange(AiPlanChangeOperation.MODIFIED, T1, S1,
                List.of("position"), null, null, null, null, null, null, new AiRelativePlacement(T1, null), null)), List.of()));
    }
    @Test void rejectsForbiddenFieldName() {
        assertInvalid(response(List.of(), List.of(new AiTaskChange(AiPlanChangeOperation.MODIFIED, T1, S1,
                List.of("status"), null, null, null, null, null, null, place(), null)), List.of()));
    }
    @Test void rejectsDuplicateElementChange() {
        assertInvalid(response(List.of(), List.of(modifiedTask("A", S1), modifiedTask("B", S1)), List.of()));
    }
    @Test void rejectsDatesOutsideProjectAndUnchangedDiff() {
        assertInvalid(response(List.of(), List.of(new AiTaskChange(AiPlanChangeOperation.MODIFIED, T1, S1,
                List.of("dueDate"), null, null, null, null, null, LocalDate.of(2030, 1, 1), place(), null)), List.of()));
        assertInvalid(response(List.of(), List.of(modifiedTask("Aufgabe 1", S1)), List.of()));
    }
    @Test void acceptsExplicitNotApplicableWithoutDiffAsBusinessDecision() {
        var response = new AiPlanChangeResponse(AiPlanChangeApplicability.NOT_APPLICABLE,
                "Eine Dehnroutine gehört nicht zum Umzugsplan.", "Keine passende Planänderung.",
                List.of(), List.of(), List.of());
        assertThatNoException().isThrownBy(() -> validator.validate(response, plan(),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));
    }
    @Test void rejectsEmptyApplicableResponseWithoutBusinessRejection() {
        assertInvalid(new AiPlanChangeResponse(AiPlanChangeApplicability.APPLICABLE, null,
                "Keine Änderung", List.of(), List.of(), List.of()));
    }
    @Test void rejectsNotApplicableResponseThatAlsoContainsChanges() {
        assertInvalid(new AiPlanChangeResponse(AiPlanChangeApplicability.NOT_APPLICABLE, "Passt nicht", "Abgelehnt",
                List.of(), List.of(newTask(S1)), List.of()));
    }

    private void assertValid(AiPlanChangeResponse value) {
        assertThatNoException().isThrownBy(() -> validator.validate(value, plan(),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));
    }
    private void assertInvalid(AiPlanChangeResponse value) {
        assertThatThrownBy(() -> validator.validate(value, plan(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
                .isInstanceOf(AiOutputValidationException.class);
    }
    private AiPlanChangeResponse response(List<AiSectionChange> s, List<AiTaskChange> t, List<AiMilestoneChange> m) {
        return new AiPlanChangeResponse("Kurz erklärt", s, t, m);
    }
    private AiTaskChange newTask(String section) { return new AiTaskChange(AiPlanChangeOperation.NEW, null, section,
            List.of("title", "priority"), "Neue Aufgabe", null, TaskPriority.MEDIUM, null, null, null, place(), null); }
    private AiTaskChange modifiedTask(String title, String section) { return modifiedTaskWithId(T1, section, title); }
    private AiTaskChange modifiedTaskWithId(String id, String section) { return modifiedTaskWithId(id, section, "Neu"); }
    private AiTaskChange modifiedTaskWithId(String id, String section, String title) { return new AiTaskChange(
            AiPlanChangeOperation.MODIFIED, id, section, List.of("title"), title, null, null, null, null, null, place(), null); }
    private AiRelativePlacement place() { return new AiRelativePlacement(null, null); }
    private AiImprovementPlanContext plan() {
        var task1 = new AiImprovementPlanContext.Element(AiImprovementElementType.TASK, T1, "Aufgabe 1", "Text", 1,
                TaskPriority.MEDIUM, 2, TaskStatus.OPEN, null, LocalDate.of(2026, 2, 1), null, List.of());
        var milestone = new AiImprovementPlanContext.Element(AiImprovementElementType.MILESTONE, M1, "Ziel", null, 2,
                null, null, null, null, LocalDate.of(2026, 2, 5), false, List.of());
        var task2 = new AiImprovementPlanContext.Element(AiImprovementElementType.TASK, T2, "Aufgabe 2", null, 1,
                TaskPriority.LOW, null, TaskStatus.OPEN, null, null, null, List.of(T1));
        return new AiImprovementPlanContext(SortMode.MANUAL, null, List.of(
                new AiImprovementPlanContext.Section(S1, "Bereich 1", "Alt", 1, List.of(task1, milestone)),
                new AiImprovementPlanContext.Section(S2, "Bereich 2", null, 2, List.of(task2))));
    }
}
