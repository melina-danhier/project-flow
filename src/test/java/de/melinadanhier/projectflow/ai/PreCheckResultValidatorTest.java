package de.melinadanhier.projectflow.ai;

import de.melinadanhier.projectflow.ai.exception.AiOutputValidationException;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckProblem;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckResult;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckSeverity;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckProblemType;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckInputChange;
import de.melinadanhier.projectflow.ai.validation.precheck.PreCheckResultValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PreCheckResultValidatorTest {

    private final PreCheckResultValidator validator = new PreCheckResultValidator(
            jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator());

    @Test
    void acceptsValidPreCheckResult() {
        assertThatCode(() -> validator.validate(new AiPreCheckResult(List.of(
                new AiPreCheckProblem(AiPreCheckSeverity.WARNING, "Zeitraum knapp", "Umfang reduzieren")))))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void rejectsProblemsWithoutConcreteSuggestion(String suggestion) {
        assertThatThrownBy(() -> validator.validate(new AiPreCheckResult(List.of(
                new AiPreCheckProblem(AiPreCheckSeverity.WARNING, "Zeitraum knapp", suggestion)))))
                .isInstanceOf(AiOutputValidationException.class);
    }

    @Test
    void rejectsNullAndBeanValidationViolations() {
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(AiOutputValidationException.class);
        assertThatThrownBy(() -> validator.validate(new AiPreCheckResult(List.of(
                new AiPreCheckProblem(AiPreCheckSeverity.ERROR, "", "Vorgaben prüfen")))))
                .isInstanceOf(AiOutputValidationException.class);
    }

    @Test
    void rejectsOpenPointWithoutAcceptedInterpretation() {
        assertThatThrownBy(() -> validator.validate(new AiPreCheckResult(List.of(
                new AiPreCheckProblem(AiPreCheckSeverity.WARNING, AiPreCheckProblemType.ASSUMPTION,
                        "Eine Planungsgrundlage ist offen.", "Ergänze die Angabe.", " ")))))
                .isInstanceOf(AiOutputValidationException.class);
    }

    @Test
    void rejectsSeverityAndProblemTypeMismatch() {
        assertThatThrownBy(() -> validator.validate(new AiPreCheckResult(List.of(
                new AiPreCheckProblem(AiPreCheckSeverity.ERROR, AiPreCheckProblemType.RISK,
                        "Angaben widersprechen sich.", "Korrigiere die Angaben.", "")))))
                .isInstanceOf(AiOutputValidationException.class);
        assertThatThrownBy(() -> validator.validate(new AiPreCheckResult(List.of(
                new AiPreCheckProblem(AiPreCheckSeverity.WARNING, AiPreCheckProblemType.CONFLICT,
                        "Eine Grundlage ist offen.", "Prüfe die Angabe.", "Die Angabe gilt.")))))
                .isInstanceOf(AiOutputValidationException.class);
    }

    @Test
    void criticalAssumptionRequiresConcretePreferredChange() {
        assertThatCode(() -> validator.validate(new AiPreCheckResult(List.of(
                new AiPreCheckProblem(AiPreCheckSeverity.WARNING,
                        AiPreCheckProblemType.CRITICAL_ASSUMPTION,
                        "Der Zeitraum ist unrealistisch.",
                        "Du kannst verschiedene Angaben ändern.",
                        "Die Dauer wird von 2 auf 10 Tage geändert.",
                        List.of(new AiPreCheckInputChange("durationDays", "2", "10")))))))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> validator.validate(new AiPreCheckResult(List.of(
                new AiPreCheckProblem(AiPreCheckSeverity.WARNING,
                        AiPreCheckProblemType.CRITICAL_ASSUMPTION,
                        "Der Zeitraum ist unrealistisch.", "Verlängere den Zeitraum.",
                        "Die Dauer wird geändert.", List.of())))))
                .isInstanceOf(AiOutputValidationException.class);
    }

    @Test
    void rejectsVagueCriticalChangesAndAcceptsConcreteDatesAndExplicitScope() {
        assertThatThrownBy(() -> validator.validate(new AiPreCheckResult(List.of(
                new AiPreCheckProblem(AiPreCheckSeverity.WARNING,
                        AiPreCheckProblemType.CRITICAL_ASSUMPTION,
                        "Der vollständige Java-Umfang ist in zwei Tagen unrealistisch.",
                        "Zeitraum erweitern oder Lernumfang reduzieren.", "Ein längerer Zeitraum wird verwendet.",
                        List.of(new AiPreCheckInputChange("durationDays", "2", "längerer Zeitraum")))))))
                .isInstanceOf(AiOutputValidationException.class);

        assertThatCode(() -> validator.validate(new AiPreCheckResult(List.of(
                new AiPreCheckProblem(AiPreCheckSeverity.WARNING,
                        AiPreCheckProblemType.CRITICAL_ASSUMPTION,
                        "Zwei Tage reichen bei zwei Stunden täglich ohne Java-Vorkenntnisse nicht für den vollständigen Java-/Spring-Boot-Umfang; kein Thema darf ausgelassen werden.",
                        "Zeitraum verlängern, tägliche Lernzeit erhöhen oder weniger Themen bearbeiten.",
                        "Das Enddatum wird von 15.09.2026 auf 25.10.2026 geändert. 2 Stunden täglich und alle Themen bleiben erhalten.",
                        List.of(new AiPreCheckInputChange("endDate", "2026-09-15", "2026-10-25")))))))
                .doesNotThrowAnyException();

        assertThatCode(() -> validator.validate(new AiPreCheckResult(List.of(
                new AiPreCheckProblem(AiPreCheckSeverity.WARNING,
                        AiPreCheckProblemType.CRITICAL_ASSUMPTION,
                        "Der Themenumfang muss reduziert werden.",
                        "Zeitraum verlängern, Lernzeit erhöhen oder Themenumfang reduzieren.",
                        "Der Umfang wird von 22 Java- und Spring-Themen auf Syntax, OOP, Collections, Exceptions, Streams, Spring Core, Spring MVC, JPA geändert.",
                        List.of(new AiPreCheckInputChange("projectGoal", "22 Java- und Spring-Themen",
                                "Syntax, OOP, Collections, Exceptions, Streams, Spring Core, Spring MVC, JPA")))))))
                .doesNotThrowAnyException();
    }

    @Test
    void workingTimeSimpleWeekRemainsWeek() {
        var problem = new AiPreCheckProblem(
                AiPreCheckSeverity.WARNING,
                AiPreCheckProblemType.CRITICAL_ASSUMPTION,
                "Zeitrahmen zu knapp.",
                "Mehr Arbeitszeit einplanen oder den Projektumfang reduzieren.",
                "Die Arbeitszeit wird von 2 Stunden pro Woche auf 4 Stunden pro Woche erhöht.",
                List.of(new AiPreCheckInputChange("availableWorkingTime", "2 Stunden pro Woche", "4 Stunden pro Woche"))
        );
        assertThatCode(() -> validator.validate(new AiPreCheckResult(List.of(problem))))
                .doesNotThrowAnyException();
    }

    @Test
    void workingTimeSimpleDayRemainsDay() {
        var problem = new AiPreCheckProblem(
                AiPreCheckSeverity.WARNING,
                AiPreCheckProblemType.CRITICAL_ASSUMPTION,
                "Zeitrahmen zu knapp.",
                "Tägliche Arbeitszeit anpassen.",
                "Die tägliche Zeit wird von 1 Stunde pro Tag auf 2 Stunden pro Tag verdoppelt.",
                List.of(new AiPreCheckInputChange("availableWorkingTime", "1 Stunde pro Tag", "2 Stunden pro Tag"))
        );
        assertThatCode(() -> validator.validate(new AiPreCheckResult(List.of(problem))))
                .doesNotThrowAnyException();
    }

    @Test
    void workingTimeSimpleWeekendRemainsWeekend() {
        var problem = new AiPreCheckProblem(
                AiPreCheckSeverity.WARNING,
                AiPreCheckProblemType.CRITICAL_ASSUMPTION,
                "Zeitrahmen zu knapp.",
                "Wochenendzeit anpassen.",
                "Die Arbeitszeit am Wochenende wird von 4 Stunden pro Wochenende auf 8 Stunden pro Wochenende angepasst.",
                List.of(new AiPreCheckInputChange("availableWorkingTime", "4 Stunden pro Wochenende", "8 Stunden pro Wochenende"))
        );
        assertThatCode(() -> validator.validate(new AiPreCheckResult(List.of(problem))))
                .doesNotThrowAnyException();
    }

    @Test
    void workingTimeTotalRemainsTotal() {
        var problem = new AiPreCheckProblem(
                AiPreCheckSeverity.WARNING,
                AiPreCheckProblemType.CRITICAL_ASSUMPTION,
                "Zeitrahmen zu knapp.",
                "Gesamtaufwand überdenken.",
                "Der Gesamtaufwand wird von 10 Stunden gesamt auf 25 Stunden gesamt korrigiert.",
                List.of(new AiPreCheckInputChange("availableWorkingTime", "10 Stunden gesamt", "25 Stunden gesamt"))
        );
        assertThatCode(() -> validator.validate(new AiPreCheckResult(List.of(problem))))
                .doesNotThrowAnyException();

        var problemStandaloneHours = new AiPreCheckProblem(
                AiPreCheckSeverity.WARNING,
                AiPreCheckProblemType.CRITICAL_ASSUMPTION,
                "Zeitrahmen zu knapp.",
                "Gesamtaufwand anpassen.",
                "Die Gesamtzeit wird von 10 Stunden auf 25 Stunden korrigiert.",
                List.of(new AiPreCheckInputChange("availableWorkingTime", "10 Stunden", "25 Stunden"))
        );
        assertThatCode(() -> validator.validate(new AiPreCheckResult(List.of(problemStandaloneHours))))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsWorkingTimeReferenceFormMismatch() {
        var problemMismatch = new AiPreCheckProblem(
                AiPreCheckSeverity.WARNING,
                AiPreCheckProblemType.CRITICAL_ASSUMPTION,
                "Zeitrahmen zu knapp.",
                "Arbeitszeit erhöhen.",
                "Die Arbeitszeit wird von 2 Stunden pro Woche auf 20 Stunden gesamt geändert.",
                List.of(new AiPreCheckInputChange("availableWorkingTime", "2 Stunden pro Woche", "20 Stunden gesamt"))
        );
        assertThatThrownBy(() -> validator.validate(new AiPreCheckResult(List.of(problemMismatch))))
                .isInstanceOf(AiOutputValidationException.class)
                .hasMessageContaining("PROPOSED_WORKING_TIME_INVALID");

        var problemDayMismatch = new AiPreCheckProblem(
                AiPreCheckSeverity.WARNING,
                AiPreCheckProblemType.CRITICAL_ASSUMPTION,
                "Zeitrahmen zu knapp.",
                "Arbeitszeit erhöhen.",
                "Die Arbeitszeit wird von 1 Stunde pro Tag auf 10 Stunden pro Woche geändert.",
                List.of(new AiPreCheckInputChange("availableWorkingTime", "1 Stunde pro Tag", "10 Stunden pro Woche"))
        );
        assertThatThrownBy(() -> validator.validate(new AiPreCheckResult(List.of(problemDayMismatch))))
                .isInstanceOf(AiOutputValidationException.class)
                .hasMessageContaining("PROPOSED_WORKING_TIME_INVALID");
    }

    @Test
    void acceptsComplexWeekdayDistributionNormalizedToPerWeekWithoutSumConstraint() {
        var problem = new AiPreCheckProblem(
                AiPreCheckSeverity.WARNING,
                AiPreCheckProblemType.CRITICAL_ASSUMPTION,
                "Verteilung unzureichend.",
                "Wöchentliche Arbeitszeit erhöhen.",
                "Die wöchentliche Arbeitszeit wird auf 8 Stunden pro Woche erhöht.",
                List.of(new AiPreCheckInputChange("availableWorkingTime",
                        "Montag 2 h, Dienstag 1 h, Donnerstag 3 h", "8 Stunden pro Woche"))
        );
        assertThatCode(() -> validator.validate(new AiPreCheckResult(List.of(problem))))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsComplexWeekendDistributionNormalizedToPerWeekend() {
        var problem = new AiPreCheckProblem(
                AiPreCheckSeverity.WARNING,
                AiPreCheckProblemType.CRITICAL_ASSUMPTION,
                "Wochenendaufwand unzureichend.",
                "Wochenendzeit anpassen.",
                "Die Wochenendzeit wird auf 8 Stunden pro Wochenende angepasst.",
                List.of(new AiPreCheckInputChange("availableWorkingTime",
                        "Samstag 3 h, Sonntag 2 h", "8 Stunden pro Wochenende"))
        );
        assertThatCode(() -> validator.validate(new AiPreCheckResult(List.of(problem))))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Montag 2h, Dienstag 3h",
            "10 Stunden pro Monat",
            "mehr Arbeitszeit einplanen",
            "0 Stunden pro Woche",
            "-2 Stunden pro Tag",
            "10 h pro Woche",
            "wöchentlich 5 Stunden"
    })
    void rejectsInvalidOrNonCanonicalWorkingTimeRecommendations(String invalidNewValue) {
        var problem = new AiPreCheckProblem(
                AiPreCheckSeverity.WARNING,
                AiPreCheckProblemType.CRITICAL_ASSUMPTION,
                "Zeitrahmen zu knapp.",
                "Mehr Arbeitszeit einplanen oder den Projektumfang reduzieren.",
                "Die Arbeitszeit wird geändert auf " + invalidNewValue + ".",
                List.of(new AiPreCheckInputChange("availableWorkingTime", "nicht angegeben", invalidNewValue))
        );
        assertThatThrownBy(() -> validator.validate(new AiPreCheckResult(List.of(problem))))
                .isInstanceOf(AiOutputValidationException.class)
                .hasMessageContaining("PROPOSED_WORKING_TIME_INVALID");
    }

    @Test
    void generalSuggestedUserActionAllowsFreeFormTextWithoutStrictTimeValues() {
        var problem = new AiPreCheckProblem(
                AiPreCheckSeverity.WARNING,
                AiPreCheckProblemType.CRITICAL_ASSUMPTION,
                "Aufwand übersteigt verfügbare Zeit.",
                "Mehr Arbeitszeit einplanen oder den Projektumfang reduzieren.",
                "Die Arbeitszeit wird von 2 Stunden pro Woche auf 6 Stunden pro Woche erhöht.",
                List.of(new AiPreCheckInputChange("availableWorkingTime", "2 Stunden pro Woche", "6 Stunden pro Woche"))
        );
        assertThatCode(() -> validator.validate(new AiPreCheckResult(List.of(problem))))
                .doesNotThrowAnyException();
    }

    @Test
    void warningAcceptedInterpretationAllowsPureDescriptiveAssumptionsWithoutProposedChanges() {
        var problem = new AiPreCheckProblem(
                AiPreCheckSeverity.WARNING,
                AiPreCheckProblemType.ASSUMPTION,
                "Die verfügbare Arbeitszeit ist knapp bemessen.",
                "Bei Bedarf mehr Zeit einplanen oder Aufgaben delegieren.",
                "Wir gehen davon aus, dass die Vorbereitung in flexiblen Zeitfenstern stattfindet und die angegebene Zeit ausreicht.",
                List.of()
        );
        assertThatCode(() -> validator.validate(new AiPreCheckResult(List.of(problem))))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsMoreThanOneProposedInputChangePerProblem() {
        var problem = new AiPreCheckProblem(
                AiPreCheckSeverity.WARNING,
                AiPreCheckProblemType.CRITICAL_ASSUMPTION,
                "Zeitrahmen und Ressourcen passen nicht.",
                "Zeitraum verlängern oder Umzugsunternehmen beauftragen.",
                "Das Enddatum wird von 21.09.2026 auf 30.09.2026 geändert und Helfer von Keine Helfer auf Umzugsfirma.",
                List.of(
                        new AiPreCheckInputChange("endDate", "2026-09-21", "2026-09-30"),
                        new AiPreCheckInputChange("constraints", "Keine Helfer", "Umzugsfirma")
                )
        );

        assertThatThrownBy(() -> validator.validate(new AiPreCheckResult(List.of(problem))))
                .isInstanceOf(AiOutputValidationException.class)
                .hasMessageContaining("PROPOSED_INPUT_CHANGES_TOO_MANY");
    }

    @Test
    void acceptsMultipleSeparateProblemsEachWithOneProposedInputChange() {
        var problem1 = new AiPreCheckProblem(
                AiPreCheckSeverity.WARNING,
                AiPreCheckProblemType.CRITICAL_ASSUMPTION,
                "Der Zeitraum ist zu kurz.",
                "Zeitraum verlängern oder Arbeitszeit erhöhen.",
                "Das Enddatum wird von 21.09.2026 auf 30.09.2026 geändert.",
                List.of(new AiPreCheckInputChange("endDate", "2026-09-21", "2026-09-30"))
        );
        var problem2 = new AiPreCheckProblem(
                AiPreCheckSeverity.WARNING,
                AiPreCheckProblemType.CRITICAL_ASSUMPTION,
                "Die Arbeitszeit reicht für diesen Umfang nicht aus.",
                "Arbeitszeit erhöhen oder Umfang reduzieren.",
                "Die Arbeitszeit wird von 2 Stunden pro Woche auf 10 Stunden pro Woche erhöht.",
                List.of(new AiPreCheckInputChange("availableWorkingTime", "2 Stunden pro Woche", "10 Stunden pro Woche"))
        );

        assertThatCode(() -> validator.validate(new AiPreCheckResult(List.of(problem1, problem2))))
                .doesNotThrowAnyException();
    }
}
