package de.melinadanhier.projectflow.ai.provider.stub;

import de.melinadanhier.projectflow.ai.provider.AiClient;
import de.melinadanhier.projectflow.ai.model.generation.*;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckProblem;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckRequest;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckResult;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckSeverity;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckProblemType;
import de.melinadanhier.projectflow.ai.model.improvement.AiImprovementRequest;
import de.melinadanhier.projectflow.ai.model.improvement.AiImprovementResponse;
import de.melinadanhier.projectflow.ai.model.planchange.*;

import java.time.LocalDate;
import java.util.List;

public class StubAiClient implements AiClient {

    private final StubAiProperties properties;

    public StubAiClient(StubAiProperties properties) {
        this.properties = properties;
    }

    @Override
    public AiPreCheckResult preCheck(AiPreCheckRequest request) {
        return switch (properties.getPreCheckScenario()) {
            case NO_PROBLEMS -> AiPreCheckResult.withoutIssues();
            case WARNING -> response(warning());
            case ERROR -> response(error());
            case MULTIPLE_ISSUES -> response(warning(), error());
        };
    }

    @Override
    public GeneratedPlanResponse generatePlan(AiGenerationRequest request) {
        LocalDate projectStart = request.confirmedWizardData().startDate();
        LocalDate projectEnd = request.confirmedWizardData().endDate();
        boolean withDates = properties.getGenerationScenario() == StubAiGenerationScenario.WITH_DATES
                && projectStart != null
                && projectEnd != null
                && !projectEnd.isBefore(projectStart);
        LocalDate scheduleStart = withDates ? projectStart : null;

        return new GeneratedPlanResponse(List.of(
                preparationSection(scheduleStart, projectEnd),
                implementationSection(scheduleStart, projectEnd)));
    }

    @Override
    public AiImprovementResponse improveElement(AiImprovementRequest request) {
        var element = request.element();
        String description = switch (request.feedbackType()) {
            case IMPROVE -> element.description() == null ? null : element.description().trim().replaceAll("\\s+", " ");
            case EXPAND -> append(element.description(), "Ergänzende Details unterstützen die Umsetzung.");
            case SIMPLIFY -> element.description() == null ? null
                    : element.description().substring(0, Math.min(80, element.description().length()));
            case REPLAN, ESTIMATE_EFFORT -> element.description();
        };
        return new AiImprovementResponse(element.elementType(), element.title(), description,
                element.priority(),
                request.feedbackType() == de.melinadanhier.projectflow.ai.model.improvement.AiFeedbackType.ESTIMATE_EFFORT
                        && element.estimatedHours() == null ? 2 : element.estimatedHours(),
                element.startDate(), element.dueDate(),
                request.feedbackType() == de.melinadanhier.projectflow.ai.model.improvement.AiFeedbackType.REPLAN
                        ? de.melinadanhier.projectflow.ai.model.improvement.AiReplanPlacementResponse.unchanged()
                        : null,
                switch (request.feedbackType()) {
                    case REPLAN -> "Die Termine passen zur Reihenfolge und zum Zeitraum des aktuellen Projektplans.";
                    case ESTIMATE_EFFORT -> "Der Aufwand berücksichtigt Inhalt und Umfang der Aufgabe.";
                    case IMPROVE, EXPAND, SIMPLIFY -> null;
                });
    }

    @Override
    public AiPlanChangeResponse proposePlanChanges(AiPlanChangeRequest request) {
        var firstSection = request.currentPlan().sections().stream()
                .filter(section -> section.reference() != null).findFirst().orElse(null);
        if (firstSection == null) return new AiPlanChangeResponse(
                AiPlanChangeApplicability.APPLICABLE, null, "Ein neuer Bereich ergänzt den Plan.",
                List.of(new AiSectionChange(AiPlanChangeOperation.NEW, null, "new-section-1",
                        List.of("title", "description"), "Ergänzungen", "Vorgeschlagene Ergänzungen",
                        null, null, null)), List.of(), List.of());
        return new AiPlanChangeResponse(AiPlanChangeApplicability.APPLICABLE, null,
                "Eine passende Aufgabe ergänzt den vorhandenen Plan.", List.of(),
                List.of(new AiTaskChange(AiPlanChangeOperation.NEW, null, firstSection.reference(),
                        List.of("title", "description", "priority"), "Änderungswunsch umsetzen",
                        request.changeRequest(), de.melinadanhier.projectflow.planelement.model.TaskPriority.MEDIUM,
                        null, null, null, new AiRelativePlacement(null, null), null)), List.of());
    }

    private String append(String current, String addition) {
        return current == null || current.isBlank() ? addition : current + " " + addition;
    }

    private GeneratedSection preparationSection(LocalDate projectStart, LocalDate projectEnd) {
        LocalDate startDate = date(projectStart, projectEnd, 0);
        LocalDate requirementsDueDate = date(projectStart, projectEnd, 1);
        LocalDate endDate = date(projectStart, projectEnd, 2);

        List<GeneratedTask> tasks = List.of(
                task("task-1", "Anforderungen festhalten", GeneratedElementOrigin.USER_INPUT,
                        100, startDate, requirementsDueDate),
                task("task-2", "Ressourcen organisieren", GeneratedElementOrigin.AI_INFERRED,
                        200, requirementsDueDate, endDate));
        GeneratedMilestone milestone = new GeneratedMilestone(
                "milestone-1", "Vorbereitung abgeschlossen", endDate, 300);

        return new GeneratedSection(
                "section-1", "Vorbereitung", "Grundlagen und Organisation",
                100, tasks, List.of(milestone));
    }

    private GeneratedSection implementationSection(LocalDate projectStart, LocalDate projectEnd) {
        LocalDate startDate = date(projectStart, projectEnd, 3);
        LocalDate executionDueDate = date(projectStart, projectEnd, 5);
        LocalDate endDate = date(projectStart, projectEnd, 6);

        List<GeneratedTask> tasks = List.of(
                task("task-3", "Kernaufgabe durchführen", GeneratedElementOrigin.AI_INFERRED,
                        100, startDate, executionDueDate),
                task("task-4", "Ergebnis kontrollieren", GeneratedElementOrigin.AI_INFERRED,
                        200, endDate, endDate));
        GeneratedMilestone milestone = new GeneratedMilestone(
                "milestone-2", "Projektziel erreicht", endDate, 300);

        return new GeneratedSection(
                "section-2", "Umsetzung", "Geplante Schritte durchführen",
                200, tasks, List.of(milestone));
    }

    private AiPreCheckResult response(AiPreCheckProblem... problems) {
        return new AiPreCheckResult(List.of(problems));
    }

    private AiPreCheckProblem warning() {
        return new AiPreCheckProblem(
                AiPreCheckSeverity.WARNING,
                AiPreCheckProblemType.RISK,
                "Der vorgesehene Zeitraum ist für den beschriebenen Umfang sehr knapp.",
                "Plane mehr Zeit ein oder reduziere den Umfang.",
                "Die Planung bleibt im angegebenen Zeitraum und priorisiert die wichtigsten Arbeiten."
        );
    }

    private AiPreCheckProblem error() {
        return new AiPreCheckProblem(
                AiPreCheckSeverity.ERROR,
                AiPreCheckProblemType.CONFLICT,
                "Die genannten Rahmenbedingungen widersprechen dem gewünschten Projektziel.",
                "Passe das Ziel oder die Rahmenbedingungen an.",
                ""
        );
    }

    private GeneratedTask task(
            String tempId,
            String title,
            GeneratedElementOrigin origin,
            int order,
            LocalDate startDate,
            LocalDate dueDate
    ) {
        return new GeneratedTask(
                tempId, title, "Plausibler Beispielschritt für Workflow- und UI-Tests.",
                2, startDate, dueDate, origin, order);
    }

    private LocalDate date(LocalDate projectStart, LocalDate projectEnd, int offsetDays) {
        if (projectStart == null) return null;
        LocalDate candidate = projectStart.plusDays(offsetDays);
        return projectEnd != null && candidate.isAfter(projectEnd) ? projectEnd : candidate;
    }
}
