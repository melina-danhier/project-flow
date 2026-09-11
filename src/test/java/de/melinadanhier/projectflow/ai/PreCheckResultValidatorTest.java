package de.melinadanhier.projectflow.ai;

import de.melinadanhier.projectflow.ai.exception.AiOutputValidationException;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckProblem;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckResult;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckSeverity;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckProblemType;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckInputChange;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckAdjustmentOption;
import de.melinadanhier.projectflow.ai.validation.precheck.PreCheckResultValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

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
    void acceptsRiskAndAssumptionThroughTheSameOpenPointContract() {
        for (var type : List.of(AiPreCheckProblemType.RISK, AiPreCheckProblemType.ASSUMPTION)) {
            assertThatCode(() -> validator.validate(new AiPreCheckResult(List.of(
                    new AiPreCheckProblem(AiPreCheckSeverity.WARNING, type,
                            "Eine Planungsgrundlage ist offen.", "Ergänze die Angabe.",
                            "Die Planung verwendet die bestätigte Auslegung.")))))
                    .doesNotThrowAnyException();
        }
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
    void criticalAssumptionRequiresGeneralOptionsAndConcretePreferredChange() {
        assertThatCode(() -> validator.validate(new AiPreCheckResult(List.of(
                new AiPreCheckProblem(AiPreCheckSeverity.WARNING,
                        AiPreCheckProblemType.CRITICAL_ASSUMPTION,
                        "Der Zeitraum ist unrealistisch.",
                        "Du kannst verschiedene Angaben ändern.",
                        "Die Dauer wird von 2 auf 10 Tage geändert.",
                        List.of(new AiPreCheckInputChange("durationDays", "2", "10")),
                        List.of(AiPreCheckAdjustmentOption.EXTEND_TIMEFRAME,
                                AiPreCheckAdjustmentOption.INCREASE_AVAILABLE_TIME))))))
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
                        List.of(new AiPreCheckInputChange("endDate", "2026-09-15", "2026-10-25")),
                        List.of(AiPreCheckAdjustmentOption.EXTEND_TIMEFRAME,
                                AiPreCheckAdjustmentOption.INCREASE_AVAILABLE_TIME))))))
                .doesNotThrowAnyException();

        assertThatCode(() -> validator.validate(new AiPreCheckResult(List.of(
                new AiPreCheckProblem(AiPreCheckSeverity.WARNING,
                        AiPreCheckProblemType.CRITICAL_ASSUMPTION,
                        "Der Themenumfang muss reduziert werden.",
                        "Zeitraum verlängern, Lernzeit erhöhen oder Themenumfang reduzieren.",
                        "Der Umfang wird von 22 Java- und Spring-Themen auf Syntax, OOP, Collections, Exceptions, Streams, Spring Core, Spring MVC, JPA geändert.",
                        List.of(new AiPreCheckInputChange("projectGoal", "22 Java- und Spring-Themen",
                                "Syntax, OOP, Collections, Exceptions, Streams, Spring Core, Spring MVC, JPA")),
                        List.of(AiPreCheckAdjustmentOption.REDUCE_SCOPE,
                                AiPreCheckAdjustmentOption.EXTEND_TIMEFRAME))))))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsAbstractLanguageAndIsoDatesInUserFacingHints() {
        assertThatThrownBy(() -> validator.validate(new AiPreCheckResult(List.of(
                new AiPreCheckProblem(AiPreCheckSeverity.WARNING, AiPreCheckProblemType.RISK,
                        "Es besteht ein offensichtliches Missverhältnis bis 2026-09-16.",
                        "Mehr Zeit einplanen.", "Die Eingaben bleiben unverändert.", List.of())))))
                .isInstanceOf(AiOutputValidationException.class);

        var change = new AiPreCheckInputChange("endDate", "2026-09-16", "2027-03-12");
        assertThat(change.displayPreviousValue()).isEqualTo("16.09.2026");
        assertThat(change.displayNewValue()).isEqualTo("12.03.2027");
    }
}
