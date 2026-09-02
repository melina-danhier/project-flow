package de.melinadanhier.projectflow.ai;

import de.melinadanhier.projectflow.ai.exception.AiOutputValidationException;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckProblem;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckResult;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckSeverity;
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
}
