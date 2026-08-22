package de.melinadanhier.projectflow.generation.client;

import de.melinadanhier.projectflow.generation.dto.request.AiGenerationRequest;
import de.melinadanhier.projectflow.generation.dto.request.AiPreCheckRequest;
import de.melinadanhier.projectflow.generation.dto.response.AiPreCheckProblem;
import de.melinadanhier.projectflow.generation.dto.response.AiPreCheckResult;
import de.melinadanhier.projectflow.generation.dto.response.AiPreCheckSeverity;
import de.melinadanhier.projectflow.generation.dto.response.GeneratedElementOrigin;
import de.melinadanhier.projectflow.generation.dto.response.GeneratedMilestone;
import de.melinadanhier.projectflow.generation.dto.response.GeneratedPhase;
import de.melinadanhier.projectflow.generation.dto.response.GeneratedPlanMetadata;
import de.melinadanhier.projectflow.generation.dto.response.GeneratedPlanResponse;
import de.melinadanhier.projectflow.generation.dto.response.GeneratedTask;

import java.time.LocalDate;
import java.util.List;

public class StubAiClient implements AiClient {

    private final AiStubProperties properties;

    public StubAiClient(AiStubProperties properties) {
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
        boolean withDates = properties.getGenerationScenario() == StubGenerationScenario.WITH_DATES
                && projectStart != null
                && (projectEnd == null || !projectEnd.isBefore(projectStart));
        return new GeneratedPlanResponse(
                new GeneratedPlanMetadata(
                        "Beispielentwurf mit Vorbereitung und Umsetzung.",
                        List.of("Benötigte Hilfsmittel sind rechtzeitig verfügbar.")),
                List.of(
                        new GeneratedPhase(
                                "phase-1", "Vorbereitung", "Grundlagen und Organisation",
                                date(withDates, projectStart, projectEnd, 0),
                                date(withDates, projectStart, projectEnd, 2), 1,
                                List.of(
                                        task("task-1", "Anforderungen festhalten", GeneratedElementOrigin.USER_INPUT,
                                                1, date(withDates, projectStart, projectEnd, 0),
                                                date(withDates, projectStart, projectEnd, 1)),
                                        task("task-2", "Ressourcen organisieren", GeneratedElementOrigin.AI_INFERRED,
                                                2, date(withDates, projectStart, projectEnd, 1),
                                                date(withDates, projectStart, projectEnd, 2))),
                                List.of(new GeneratedMilestone(
                                        "milestone-1", "Vorbereitung abgeschlossen",
                                        date(withDates, projectStart, projectEnd, 2), 1))),
                        new GeneratedPhase(
                                "phase-2", "Umsetzung", "Geplante Schritte durchführen",
                                date(withDates, projectStart, projectEnd, 3),
                                date(withDates, projectStart, projectEnd, 6), 2,
                                List.of(
                                        task("task-3", "Kernaufgabe durchführen", GeneratedElementOrigin.AI_INFERRED,
                                                1, date(withDates, projectStart, projectEnd, 3),
                                                date(withDates, projectStart, projectEnd, 5)),
                                        task("task-4", "Ergebnis kontrollieren", GeneratedElementOrigin.AI_INFERRED,
                                                2, date(withDates, projectStart, projectEnd, 6),
                                                date(withDates, projectStart, projectEnd, 6))),
                                List.of(new GeneratedMilestone(
                                        "milestone-2", "Projektziel erreicht",
                                        date(withDates, projectStart, projectEnd, 6), 1)))));
    }

    private AiPreCheckResult response(AiPreCheckProblem... problems) {
        return new AiPreCheckResult(List.of(problems));
    }

    private AiPreCheckProblem warning() {
        return new AiPreCheckProblem(
                AiPreCheckSeverity.WARNING,
                "Der vorgesehene Zeitraum ist für den beschriebenen Umfang sehr knapp.",
                "Plane mehr Zeit ein oder reduziere den Umfang.");
    }

    private AiPreCheckProblem error() {
        return new AiPreCheckProblem(
                AiPreCheckSeverity.ERROR,
                "Die genannten Rahmenbedingungen widersprechen dem gewünschten Projektziel.",
                "Passe das Ziel oder die Rahmenbedingungen an.");
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
                2, startDate, dueDate, null, origin, order);
    }

    private LocalDate date(boolean withDates, LocalDate projectStart, LocalDate projectEnd, int offsetDays) {
        if (!withDates) {
            return null;
        }
        LocalDate candidate = projectStart.plusDays(offsetDays);
        return projectEnd != null && candidate.isAfter(projectEnd) ? projectEnd : candidate;
    }
}
