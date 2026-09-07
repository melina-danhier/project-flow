package de.melinadanhier.projectflow.ai;

import de.melinadanhier.projectflow.ai.exception.AiOutputValidationException;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckProblem;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckResult;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckSeverity;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckProblemType;
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
    void rejectsOpenPointWithoutReviewQuestion() {
        assertThatThrownBy(() -> validator.validate(new AiPreCheckResult(List.of(
                new AiPreCheckProblem(AiPreCheckSeverity.WARNING, AiPreCheckProblemType.ASSUMPTION,
                        "Eine Planungsgrundlage ist offen.", "Ergänze die Angabe.", " ",
                        "Die Planung verwendet die bestätigte Auslegung.")))))
                .isInstanceOf(AiOutputValidationException.class);
    }

    @Test
    void rejectsBlockingErrorWithReviewQuestion() {
        assertThatThrownBy(() -> validator.validate(new AiPreCheckResult(List.of(
                new AiPreCheckProblem(AiPreCheckSeverity.ERROR, AiPreCheckProblemType.CONFLICT,
                        "Angaben widersprechen sich.", "Korrigiere die Angaben.",
                        "Welche Angabe soll gelten?", "")))))
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
}
