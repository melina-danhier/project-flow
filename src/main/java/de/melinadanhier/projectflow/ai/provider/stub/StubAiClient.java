package de.melinadanhier.projectflow.ai.provider.stub;

import de.melinadanhier.projectflow.ai.provider.AiClient;
import de.melinadanhier.projectflow.ai.model.generation.*;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckProblem;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckRequest;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckResult;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckSeverity;

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
                && (projectEnd == null || !projectEnd.isBefore(projectStart));
        LocalDate scheduleStart = withDates ? projectStart : null;

        return new GeneratedPlanResponse(List.of(
                preparationSection(scheduleStart, projectEnd),
                implementationSection(scheduleStart, projectEnd)), List.of()
        );
    }

    private GeneratedSection preparationSection(LocalDate projectStart, LocalDate projectEnd) {
        LocalDate startDate = date(projectStart, projectEnd, 0);
        LocalDate requirementsDueDate = date(projectStart, projectEnd, 1);
        LocalDate endDate = date(projectStart, projectEnd, 2);

        List<GeneratedTask> tasks = List.of(
                task("task-1", "Anforderungen festhalten", GeneratedElementOrigin.USER_INPUT,
                        1, startDate, requirementsDueDate),
                task("task-2", "Ressourcen organisieren", GeneratedElementOrigin.AI_INFERRED,
                        2, requirementsDueDate, endDate));
        GeneratedMilestone milestone = new GeneratedMilestone(
                "milestone-1", "Vorbereitung abgeschlossen", endDate, 1);

        return new GeneratedSection(
                "section-1", "Vorbereitung", "Grundlagen und Organisation",
                1, tasks, List.of(milestone));
    }

    private GeneratedSection implementationSection(LocalDate projectStart, LocalDate projectEnd) {
        LocalDate startDate = date(projectStart, projectEnd, 3);
        LocalDate executionDueDate = date(projectStart, projectEnd, 5);
        LocalDate endDate = date(projectStart, projectEnd, 6);

        List<GeneratedTask> tasks = List.of(
                task("task-3", "Kernaufgabe durchführen", GeneratedElementOrigin.AI_INFERRED,
                        1, startDate, executionDueDate),
                task("task-4", "Ergebnis kontrollieren", GeneratedElementOrigin.AI_INFERRED,
                        2, endDate, endDate));
        GeneratedMilestone milestone = new GeneratedMilestone(
                "milestone-2", "Projektziel erreicht", endDate, 1);

        return new GeneratedSection(
                "section-2", "Umsetzung", "Geplante Schritte durchführen",
                2, tasks, List.of(milestone));
    }

    private AiPreCheckResult response(AiPreCheckProblem... problems) {
        return new AiPreCheckResult(List.of(problems));
    }

    private AiPreCheckProblem warning() {
        return new AiPreCheckProblem(
                AiPreCheckSeverity.WARNING,
                "Der vorgesehene Zeitraum ist für den beschriebenen Umfang sehr knapp.",
                "Plane mehr Zeit ein oder reduziere den Umfang."
        );
    }

    private AiPreCheckProblem error() {
        return new AiPreCheckProblem(
                AiPreCheckSeverity.ERROR,
                "Die genannten Rahmenbedingungen widersprechen dem gewünschten Projektziel.",
                "Passe das Ziel oder die Rahmenbedingungen an."
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
