package de.melinadanhier.projectflow.ai.provider.stub;

import de.melinadanhier.projectflow.ai.model.generation.*;
import de.melinadanhier.projectflow.ai.model.improvement.*;
import de.melinadanhier.projectflow.ai.model.planchange.*;
import de.melinadanhier.projectflow.ai.model.precheck.*;
import de.melinadanhier.projectflow.ai.provider.AiClient;
import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;
import de.melinadanhier.projectflow.plancontainer.project.model.classification.ProjectSubCategory;
import de.melinadanhier.projectflow.plancontainer.template.model.ProjectCategory;
import de.melinadanhier.projectflow.planelement.model.TaskPriority;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class StubAiClient implements AiClient {

    private static final DateTimeFormatter GERMAN_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final StubAiProperties properties;

    public StubAiClient(StubAiProperties properties) {
        this.properties = properties;
    }

    @Override
    public AiPreCheckResult preCheck(AiPreCheckRequest request) {
        AiWizardSnapshot snapshot = request != null ? request.confirmedWizardData() : null;
        return switch (properties.getPreCheckScenario()) {
            case NO_PROBLEMS -> AiPreCheckResult.withoutIssues();
            case WARNING, RISK -> response(risk());
            case ASSUMPTION -> response(assumption());
            case CRITICAL_ASSUMPTION -> response(criticalAssumption(snapshot));
            case ERROR, CONFLICT -> response(conflict());
            case MULTIPLE_WARNINGS -> response(risk(), assumption(), criticalAssumption(snapshot));
            case MULTIPLE_ISSUES -> response(risk(), conflict());
            case DYNAMIC -> dynamicPreCheck(snapshot);
        };
    }

    @Override
    public GeneratedPlanResponse generatePlan(AiGenerationRequest request) {
        AiWizardSnapshot snapshot = request.confirmedWizardData();
        LocalDate projectStart = snapshot.startDate();
        LocalDate projectEnd = snapshot.endDate();
        boolean withDates = properties.getGenerationScenario() == StubAiGenerationScenario.WITH_DATES
                && projectStart != null
                && projectEnd != null
                && !projectEnd.isBefore(projectStart);
        LocalDate scheduleStart = withDates ? projectStart : null;
        LocalDate scheduleEnd = withDates ? projectEnd : null;

        PlanTemplate template = selectPlanTemplate(snapshot);
        return instantiatePlan(template, scheduleStart, scheduleEnd);
    }

    @Override
    public AiImprovementResponse improveElement(AiImprovementRequest request) {
        var element = request.element();
        var type = element.elementType();
        var action = request.feedbackType();

        String title = element.title();
        String description = element.description();
        TaskPriority priority = type == AiImprovementElementType.TASK
                ? (element.priority() != null ? element.priority() : TaskPriority.MEDIUM)
                : null;
        Integer estimatedMinutes = type == AiImprovementElementType.TASK
                ? (action == AiFeedbackType.ESTIMATE_EFFORT
                        ? (element.estimatedMinutes() != null ? element.estimatedMinutes() : 120)
                        : element.estimatedMinutes())
                : null;
        LocalDate startDate = type == AiImprovementElementType.TASK ? element.startDate() : null;
        LocalDate dueDate = type == AiImprovementElementType.SECTION ? null : element.dueDate();

        switch (action) {
            case IMPROVE -> {
                title = title == null ? null : title.trim();
                description = description == null ? null : description.trim().replaceAll("\\s+", " ");
            }
            case EXPAND -> description = append(description, "Ergänzende Details und Hinweise unterstützen die strukturierte Umsetzung.");
            case SIMPLIFY -> {
                if (description != null && description.length() > 60) {
                    description = description.substring(0, Math.min(80, description.length())).trim();
                }
            }
            case REPLAN -> {
                // Bei Replan bleiben bestehende Termine konsistent oder werden validiert
            }
            case ESTIMATE_EFFORT -> {
                if (estimatedMinutes == null) {
                    estimatedMinutes = 120;
                }
            }
        }

        AiReplanPlacementResponse placement = action == AiFeedbackType.REPLAN
                ? AiReplanPlacementResponse.unchanged()
                : null;

        String explanation = switch (action) {
            case REPLAN -> "Die zeitliche Einordnung und Reihenfolge passt optimal zum aktuellen Gesamtplan.";
            case ESTIMATE_EFFORT -> "Der geschätzte Aufwand von " + estimatedMinutes + " Minuten ist für diese Aufgabe angemessen.";
            case IMPROVE, EXPAND, SIMPLIFY -> null;
        };

        return new AiImprovementResponse(
                type, title, description, priority, estimatedMinutes, startDate, dueDate, placement, explanation);
    }

    @Override
    public AiPlanChangeResponse proposePlanChanges(AiPlanChangeRequest request) {
        String wish = request.changeRequest() != null ? request.changeRequest().trim() : "";
        String lowerWish = wish.toLowerCase(Locale.GERMAN);

        // Fachfremde Änderungswünsche ablehnen
        if (lowerWish.contains("ablehnen") || lowerWish.contains("yoga")
                || lowerWish.contains("klimawandel") || lowerWish.contains("nicht passend")
                || lowerWish.contains("löschen") || lowerWish.contains("unpassend")
                || lowerWish.contains("fachfremd")) {
            return new AiPlanChangeResponse(
                    AiPlanChangeApplicability.NOT_APPLICABLE,
                    "Der Änderungswunsch passt nicht zum fachlichen Ziel dieses Projekts.",
                    "Der Änderungswunsch liegt außerhalb des Projektumfangs und wird nicht in den Plan übernommen.",
                    List.of(), List.of(), List.of()
            );
        }

        var sections = request.currentPlan() != null && request.currentPlan().sections() != null
                ? request.currentPlan().sections()
                : List.<AiImprovementPlanContext.Section>of();

        var firstSection = sections.stream()
                .filter(s -> s.reference() != null)
                .findFirst()
                .orElse(null);

        // Neuer Bereich gewünscht oder noch keine Section vorhanden
        if (firstSection == null || lowerWish.contains("bereich") || lowerWish.contains("section") || lowerWish.contains("phase")) {
            String newSectionRef = "new-section-1";
            var sectionChange = new AiSectionChange(
                    AiPlanChangeOperation.NEW,
                    null,
                    newSectionRef,
                    List.of("title", "description"),
                    "Ergänzender Bereich",
                    "Zusätzlicher Bereich für den gewünschten Projektschritt.",
                    null,
                    null,
                    null
            );
            var taskChange = new AiTaskChange(
                    AiPlanChangeOperation.NEW,
                    null,
                    newSectionRef,
                    List.of("title", "description", "priority", "estimatedMinutes"),
                    "Zusatzaufgabe durchführen",
                    wish.isBlank() ? "Neue Aufgabe im ergänzenden Bereich." : wish,
                    TaskPriority.MEDIUM,
                    120,
                    null,
                    null,
                    new AiRelativePlacement(null, null),
                    "Die Aufgabe konkretisiert den neu hinzugefügten Bereich."
            );
            return new AiPlanChangeResponse(
                    AiPlanChangeApplicability.APPLICABLE,
                    null,
                    "Ein neuer Bereich mit einer passenden Aufgabe ergänzt den Projektplan.",
                    List.of(sectionChange),
                    List.of(taskChange),
                    List.of()
            );
        }

        // Meilenstein gewünscht
        if (lowerWish.contains("meilenstein") || lowerWish.contains("milestone")) {
            var milestoneChange = new AiMilestoneChange(
                    AiPlanChangeOperation.NEW,
                    null,
                    firstSection.reference(),
                    List.of("title", "description"),
                    "Zwischenziel erreicht",
                    wish.isBlank() ? "Vorgeschlagener Meilenstein." : wish,
                    null,
                    new AiRelativePlacement(null, null),
                    "Der Meilenstein markiert den Abschluss des gewünschten Zwischenschritts."
            );
            return new AiPlanChangeResponse(
                    AiPlanChangeApplicability.APPLICABLE,
                    null,
                    "Ein neuer Meilenstein ergänzt den vorhandenen Bereich.",
                    List.of(),
                    List.of(),
                    List.of(milestoneChange)
            );
        }

        // Standard: Neue Aufgabe im ersten Bereich
        var taskChange = new AiTaskChange(
                AiPlanChangeOperation.NEW,
                null,
                firstSection.reference(),
                List.of("title", "description", "priority", "estimatedMinutes"),
                "Änderungswunsch umsetzen",
                wish.isBlank() ? "Vorgeschlagene Aufgabe zur Planerweiterung." : wish,
                TaskPriority.MEDIUM,
                120,
                null,
                null,
                new AiRelativePlacement(null, null),
                "Die vorgeschlagene Aufgabe erweitert den bestehenden Bereich passend zur Anfrage."
        );
        return new AiPlanChangeResponse(
                AiPlanChangeApplicability.APPLICABLE,
                null,
                "Eine passende Aufgabe ergänzt den vorhandenen Plan.",
                List.of(),
                List.of(taskChange),
                List.of()
        );
    }

    private AiPreCheckResult response(AiPreCheckProblem... problems) {
        return new AiPreCheckResult(List.of(problems));
    }

    private AiPreCheckProblem risk() {
        return new AiPreCheckProblem(
                AiPreCheckSeverity.WARNING,
                AiPreCheckProblemType.RISK,
                "Der vorgesehene Zeitraum ist für den beschriebenen Umfang sehr knapp.",
                "Plane mehr Zeit ein oder reduziere den Umfang.",
                "Die Planung bleibt im angegebenen Zeitraum und priorisiert die wichtigsten Arbeiten."
        );
    }

    private AiPreCheckProblem assumption() {
        return new AiPreCheckProblem(
                AiPreCheckSeverity.WARNING,
                AiPreCheckProblemType.ASSUMPTION,
                "Für die Umsetzung wird von grundlegenden Vorkenntnissen und selbstständiger Arbeitsweise ausgegangen.",
                "Prüfe, ob vorab zusätzliche Einarbeitungszeit oder externe Unterstützung eingeplant werden sollte.",
                "Die Planung setzt grundlegende Vorkenntnisse voraus und sieht keine gesonderten Grundlagen-Schulungen vor."
        );
    }

    private AiPreCheckProblem conflict() {
        return new AiPreCheckProblem(
                AiPreCheckSeverity.ERROR,
                AiPreCheckProblemType.CONFLICT,
                "Die genannten Rahmenbedingungen widersprechen dem gewünschten Projektziel.",
                "Passe das Ziel oder die Rahmenbedingungen an.",
                ""
        );
    }

    private AiPreCheckProblem criticalAssumption(AiWizardSnapshot snapshot) {
        if (snapshot != null && snapshot.availableWorkingTime() != null
                && !snapshot.availableWorkingTime().isBlank()
                && !"nicht angegeben".equalsIgnoreCase(snapshot.availableWorkingTime().trim())) {
            String prev = snapshot.availableWorkingTime().trim();
            String lower = prev.toLowerCase(Locale.GERMAN);
            String next;
            if (lower.contains("tag")) {
                next = "4 Stunden pro Tag";
            } else if (lower.contains("wochenende")) {
                next = "8 Stunden pro Wochenende";
            } else if (lower.contains("gesamt")) {
                next = "20 Stunden gesamt";
            } else {
                next = "12 Stunden pro Woche";
            }
            return new AiPreCheckProblem(
                    AiPreCheckSeverity.WARNING,
                    AiPreCheckProblemType.CRITICAL_ASSUMPTION,
                    "Die eingeplante Arbeitszeit ist für die geplante Umsetzung knapp bemessen.",
                    "Plane mehr Arbeitszeit ein oder passe den Projektumfang an.",
                    "Die Arbeitszeit wird von " + prev + " auf " + next + " angepasst.",
                    List.of(new AiPreCheckInputChange("availableWorkingTime", prev, next))
            );
        }

        if (snapshot != null && snapshot.endDate() != null) {
            LocalDate prevDate = snapshot.endDate();
            LocalDate nextDate = prevDate.plusDays(14);
            String prevIso = prevDate.toString();
            String nextIso = nextDate.toString();
            String prevDisplay = prevDate.format(GERMAN_DATE_FORMAT);
            String nextDisplay = nextDate.format(GERMAN_DATE_FORMAT);
            return new AiPreCheckProblem(
                    AiPreCheckSeverity.WARNING,
                    AiPreCheckProblemType.CRITICAL_ASSUMPTION,
                    "Der geplante Zeitraum reicht für die sorgfältige Durchführung aller Schritte voraussichtlich nicht aus.",
                    "Verschiebe den Endtermin oder reduziere die Anzahl der Aufgaben.",
                    "Das Enddatum wird von " + prevDisplay + " auf " + nextDisplay + " geändert.",
                    List.of(new AiPreCheckInputChange("endDate", prevIso, nextIso))
            );
        }

        if (snapshot != null && snapshot.durationDays() != null && snapshot.durationDays() > 0) {
            int prevDays = snapshot.durationDays();
            int nextDays = prevDays + 14;
            return new AiPreCheckProblem(
                    AiPreCheckSeverity.WARNING,
                    AiPreCheckProblemType.CRITICAL_ASSUMPTION,
                    "Die geplante Projektdauer ist für das Vorhaben sehr kurz veranschlagt.",
                    "Verlängere die Projektdauer oder priorisiere Kernaufgaben.",
                    "Die geplante Dauer wird von " + prevDays + " auf " + nextDays + " Tage angepasst.",
                    List.of(new AiPreCheckInputChange("durationDays", String.valueOf(prevDays), String.valueOf(nextDays)))
            );
        }

        if (snapshot != null && snapshot.projectGoal() != null && !snapshot.projectGoal().isBlank()) {
            String prev = snapshot.projectGoal().trim();
            String next = prev + ", Kernaufgaben priorisieren";
            return new AiPreCheckProblem(
                    AiPreCheckSeverity.WARNING,
                    AiPreCheckProblemType.CRITICAL_ASSUMPTION,
                    "Das Projektziel umfasst sehr viele Aspekte gleichzeitig.",
                    "Fokussiere das Ziel auf die wichtigsten Kernaufgaben.",
                    "Das Projektziel wird von " + prev + " auf " + next + " angepasst.",
                    List.of(new AiPreCheckInputChange("projectGoal", prev, next))
            );
        }

        String prev = "nicht angegeben";
        String next = "8 Stunden pro Woche";
        return new AiPreCheckProblem(
                AiPreCheckSeverity.WARNING,
                AiPreCheckProblemType.CRITICAL_ASSUMPTION,
                "Ohne Angabe verfügbarer Arbeitszeit kann der Aufwand nicht verlässlich kalkuliert werden.",
                "Hinterlege ein wöchentliches Zeitbudget oder reduziere den Umfang.",
                "Die verfügbare Arbeitszeit wird auf " + next + " festgelegt.",
                List.of(new AiPreCheckInputChange("availableWorkingTime", prev, next))
        );
    }

    private AiPreCheckResult dynamicPreCheck(AiWizardSnapshot snapshot) {
        if (snapshot == null) {
            return AiPreCheckResult.withoutIssues();
        }
        String combined = String.join(" ",
                snapshot.title() == null ? "" : snapshot.title(),
                snapshot.description() == null ? "" : snapshot.description(),
                snapshot.projectGoal() == null ? "" : snapshot.projectGoal(),
                snapshot.constraints() == null ? "" : snapshot.constraints(),
                snapshot.additionalInformation() == null ? "" : snapshot.additionalInformation()
        ).toLowerCase(Locale.GERMAN);

        if (combined.contains("fehler") || combined.contains("konflikt")
                || combined.contains("error") || combined.contains("widerspruch")) {
            return response(conflict());
        }
        if (combined.contains("mehrere") || combined.contains("warnungen") || combined.contains("probleme")) {
            return response(risk(), assumption(), criticalAssumption(snapshot));
        }
        if (combined.contains("kritisch") || combined.contains("anpassen")
                || combined.contains("vorschlag") || combined.contains("ändern")) {
            return response(criticalAssumption(snapshot));
        }
        if (combined.contains("annahme") || combined.contains("voraussetzung") || combined.contains("unklar")) {
            return response(assumption());
        }
        if (combined.contains("knapp") || combined.contains("dringend")
                || combined.contains("eilig") || combined.contains("risiko")) {
            return response(risk());
        }
        return AiPreCheckResult.withoutIssues();
    }

    private record SectionDef(
            String tempId, String title, String description, int order,
            List<TaskDef> tasks, MilestoneDef milestone) {}

    private record TaskDef(
            String tempId, String title, String description, int estimatedMinutes,
            TaskPriority priority, int order, List<String> prerequisiteTempIds,
            double startFraction, double dueFraction) {}

    private record MilestoneDef(
            String tempId, String title, int order, double fraction) {}

    private record PlanTemplate(List<SectionDef> sections) {}

    private GeneratedPlanResponse instantiatePlan(PlanTemplate template, LocalDate start, LocalDate end) {
        List<GeneratedSection> sections = new ArrayList<>();
        for (var sectionDef : template.sections()) {
            List<GeneratedTask> tasks = new ArrayList<>();
            for (var taskDef : sectionDef.tasks()) {
                LocalDate taskStart = calculateDate(start, end, taskDef.startFraction());
                LocalDate taskDue = calculateDate(start, end, taskDef.dueFraction());
                tasks.add(new GeneratedTask(
                        taskDef.tempId(),
                        taskDef.title(),
                        taskDef.description(),
                        taskDef.estimatedMinutes(),
                        taskStart,
                        taskDue,
                        taskDef.order(),
                        taskDef.prerequisiteTempIds(),
                        taskDef.priority()
                ));
            }
            LocalDate milestoneDate = sectionDef.milestone() != null
                    ? calculateDate(start, end, sectionDef.milestone().fraction())
                    : null;
            List<GeneratedMilestone> milestones = sectionDef.milestone() != null
                    ? List.of(new GeneratedMilestone(
                            sectionDef.milestone().tempId(),
                            sectionDef.milestone().title(),
                            milestoneDate,
                            sectionDef.milestone().order()))
                    : List.of();
            sections.add(new GeneratedSection(
                    sectionDef.tempId(),
                    sectionDef.title(),
                    sectionDef.description(),
                    sectionDef.order(),
                    tasks,
                    milestones
            ));
        }
        return new GeneratedPlanResponse(sections);
    }

    private LocalDate calculateDate(LocalDate start, LocalDate end, double fraction) {
        if (start == null || end == null) {
            return null;
        }
        long days = ChronoUnit.DAYS.between(start, end);
        long offset = Math.round(fraction * days);
        LocalDate candidate = start.plusDays(offset);
        return candidate.isAfter(end) ? end : candidate;
    }

    private PlanTemplate selectPlanTemplate(AiWizardSnapshot snapshot) {
        String title = snapshot != null && snapshot.title() != null ? snapshot.title().toLowerCase(Locale.GERMAN) : "";
        ProjectCategory category = snapshot != null ? snapshot.category() : null;
        ProjectSubCategory subcategory = snapshot != null ? snapshot.subcategory() : null;

        if (category == ProjectCategory.HOME || subcategory == ProjectSubCategory.MOVING
                || title.contains("umzug") || title.contains("wohnung")) {
            return movingPlanTemplate();
        }
        if (category == ProjectCategory.EDUCATION || subcategory == ProjectSubCategory.PRESENTATION_OR_REPORT
                || title.contains("präsentation") || title.contains("vortrag")
                || title.contains("referat") || title.contains("studium")) {
            return educationPlanTemplate();
        }
        return defaultPlanTemplate();
    }

    private PlanTemplate movingPlanTemplate() {
        return new PlanTemplate(List.of(
                new SectionDef("section-1", "Vorbereitung und Organisation", "Grundlagen klären, Helfer und Material organisieren", 100,
                        List.of(
                                new TaskDef("task-1", "Umzugstermin und Helfer abstimmen", "Termin festlegen und Unterstützung durch Helfer anfragen.", 120, TaskPriority.HIGH, 100, List.of(), 0.0, 0.15),
                                new TaskDef("task-2", "Umzugskartons und Material besorgen", "Kisten, Packband und Schutzfolie rechtzeitig bereitstellen.", 90, TaskPriority.MEDIUM, 200, List.of("task-1"), 0.15, 0.30)
                        ),
                        new MilestoneDef("milestone-1", "Vorbereitung abgeschlossen", 300, 0.30)
                ),
                new SectionDef("section-2", "Packen und Demontage", "Gegenstände packen und Möbel abbauen", 200,
                        List.of(
                                new TaskDef("task-3", "Aussortieren und Kisten packen", "Räume systematisch durchgehen, Unnötiges aussortieren und Kisten beschriften.", 240, TaskPriority.HIGH, 100, List.of("task-2"), 0.30, 0.55),
                                new TaskDef("task-4", "Möbel demontieren und sichern", "Große Möbelstücke abbauen und Schrauben sowie Kleinteile sichern.", 180, TaskPriority.MEDIUM, 200, List.of("task-3"), 0.55, 0.70)
                        ),
                        new MilestoneDef("milestone-2", "Wohnung packbereit", 300, 0.70)
                ),
                new SectionDef("section-3", "Umzugstag und Wohnungsübergabe", "Transport durchführen und alte Wohnung übergeben", 300,
                        List.of(
                                new TaskDef("task-5", "Möbel und Kisten transportieren", "Transporter beladen, Fahrt zur neuen Wohnung und Entladen.", 300, TaskPriority.HIGH, 100, List.of("task-4"), 0.70, 0.90),
                                new TaskDef("task-6", "Alte Wohnung reinigen und übergeben", "Endreinigung durchführen, Zählerstände ablesen und Schlüssel übergeben.", 120, TaskPriority.MEDIUM, 200, List.of("task-5"), 0.90, 1.0)
                        ),
                        new MilestoneDef("milestone-3", "Umzug erfolgreich abgeschlossen", 300, 1.0)
                )
        ));
    }

    private PlanTemplate educationPlanTemplate() {
        return new PlanTemplate(List.of(
                new SectionDef("section-1", "Recherche und Themenfindung", "Thema eingrenzen und Literatur sichten", 100,
                        List.of(
                                new TaskDef("task-1", "Thema und Leitfragen festlegen", "Schwerpunkte bestimmen und Zielsetzung der Präsentation definieren.", 90, TaskPriority.HIGH, 100, List.of(), 0.0, 0.15),
                                new TaskDef("task-2", "Fachliteratur und Quellen auswerten", "Relevante Studien, Daten und Literatur strukturieren.", 180, TaskPriority.MEDIUM, 200, List.of("task-1"), 0.15, 0.30)
                        ),
                        new MilestoneDef("milestone-1", "Recherche abgeschlossen", 300, 0.30)
                ),
                new SectionDef("section-2", "Konzeption und Ausarbeitung", "Inhalte strukturieren und Folien erstellen", 200,
                        List.of(
                                new TaskDef("task-3", "Gliederung und Folien entwerfen", "Präsentationsfolien gestalten und Kernargumente visualisieren.", 240, TaskPriority.HIGH, 100, List.of("task-2"), 0.30, 0.55),
                                new TaskDef("task-4", "Handout und Notizen verfassen", "Zusammenfassung für das Publikum und Stichpunkte für den Vortrag vorbereiten.", 90, TaskPriority.MEDIUM, 200, List.of("task-3"), 0.55, 0.70)
                        ),
                        new MilestoneDef("milestone-2", "Rohfassung fertiggestellt", 300, 0.70)
                ),
                new SectionDef("section-3", "Probelauf und Vortrag", "Präsentation einüben und halten", 300,
                        List.of(
                                new TaskDef("task-5", "Vortrag proben und Zeit stoppen", "Ablauf im Team durchsprechen und Übergänge optimieren.", 120, TaskPriority.HIGH, 100, List.of("task-4"), 0.70, 0.90),
                                new TaskDef("task-6", "Präsentation halten und Feedback einholen", "Vortrag präsentieren und Fragen der Zuhörer beantworten.", 60, TaskPriority.HIGH, 200, List.of("task-5"), 0.90, 1.0)
                        ),
                        new MilestoneDef("milestone-3", "Präsentation erfolgreich gehalten", 300, 1.0)
                )
        ));
    }

    private PlanTemplate defaultPlanTemplate() {
        return new PlanTemplate(List.of(
                new SectionDef("section-1", "Planung und Vorbereitung", "Grundlagen und organisatorische Rahmenbedingungen klären", 100,
                        List.of(
                                new TaskDef("task-1", "Anforderungen und Ziele festhalten", "Wesentliche Anforderungen dokumentieren und Rahmenbedingungen abstimmen.", 120, TaskPriority.HIGH, 100, List.of(), 0.0, 0.15),
                                new TaskDef("task-2", "Ressourcen und Arbeitsschritte organisieren", "Benötigte Mittel bereitstellen und Aufgabenaufteilung planen.", 90, TaskPriority.MEDIUM, 200, List.of("task-1"), 0.15, 0.30)
                        ),
                        new MilestoneDef("milestone-1", "Vorbereitung abgeschlossen", 300, 0.30)
                ),
                new SectionDef("section-2", "Durchführung und Umsetzung", "Geplante Schritte zielgerichtet bearbeiten", 200,
                        List.of(
                                new TaskDef("task-3", "Kernaufgaben umsetzen", "Zentrale Arbeitsschritte gemäß Planung durchführen.", 240, TaskPriority.HIGH, 100, List.of("task-2"), 0.30, 0.55),
                                new TaskDef("task-4", "Zwischenergebnisse prüfen und optimieren", "Erreichten Zwischenstand kontrollieren und bei Bedarf nachjustieren.", 120, TaskPriority.MEDIUM, 200, List.of("task-3"), 0.55, 0.70)
                        ),
                        new MilestoneDef("milestone-2", "Umsetzung abgeschlossen", 300, 0.70)
                ),
                new SectionDef("section-3", "Abschluss und Nachbereitung", "Ergebnisse zusammenfassen und Vorhaben abschließen", 300,
                        List.of(
                                new TaskDef("task-5", "Gesamtergebnis kontrollieren und dokumentieren", "Vollständigkeit der Arbeitsschritte überprüfen und Dokumentation erstellen.", 90, TaskPriority.MEDIUM, 100, List.of("task-4"), 0.70, 0.90),
                                new TaskDef("task-6", "Abschlussbesprechung und Übergabe durchführen", "Projektziel offiziell abschließen und Erkenntnisse festhalten.", 60, TaskPriority.LOW, 200, List.of("task-5"), 0.90, 1.0)
                        ),
                        new MilestoneDef("milestone-3", "Projektziel erreicht", 300, 1.0)
                )
        ));
    }

    private String append(String current, String addition) {
        return current == null || current.isBlank() ? addition : current + " " + addition;
    }
}
