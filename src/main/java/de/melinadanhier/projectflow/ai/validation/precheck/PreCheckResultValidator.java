package de.melinadanhier.projectflow.ai.validation.precheck;

import de.melinadanhier.projectflow.ai.exception.AiOutputValidationException;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckResult;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckProblemType;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckSeverity;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PreCheckResultValidator {

    private final Validator validator;

    public void validate(AiPreCheckResult result) {
        if (result == null) {
            throw new AiOutputValidationException("Der KI-Pre-Check darf nicht null sein.");
        }
        List<String> issues = new ArrayList<>();
        validator.validate(result).stream()
                .sorted(Comparator.comparing(violation -> violation.getPropertyPath().toString()))
                .forEach(violation -> issues.add("BEAN_VALIDATION_FAILED | "
                        + violation.getPropertyPath() + " | " + violation.getMessage()));
        for (int index = 0; result.problems() != null && index < result.problems().size(); index++) {
            var problem = result.problems().get(index);
            if (problem != null && problem.severity() == AiPreCheckSeverity.WARNING
                    && (problem.acceptedInterpretation() == null
                    || problem.acceptedInterpretation().isBlank())) {
                issues.add("ACCEPTED_INTERPRETATION_MISSING | problems[" + index
                        + "].acceptedInterpretation");
            }
            if (problem != null && problem.severity() == AiPreCheckSeverity.WARNING
                    && (problem.reviewQuestion() == null || problem.reviewQuestion().isBlank())) {
                issues.add("REVIEW_QUESTION_MISSING | problems[" + index + "].reviewQuestion");
            }
            if (problem != null && problem.severity() == AiPreCheckSeverity.ERROR
                    && problem.reviewQuestion() != null && !problem.reviewQuestion().isBlank()) {
                issues.add("ERROR_REVIEW_QUESTION_INVALID | problems[" + index + "].reviewQuestion");
            }
            if (problem != null && problem.severity() == AiPreCheckSeverity.ERROR
                    && problem.type() != AiPreCheckProblemType.CONFLICT) {
                issues.add("ERROR_TYPE_INVALID | problems[" + index + "].type");
            }
            if (problem != null && problem.severity() == AiPreCheckSeverity.WARNING
                    && problem.type() == AiPreCheckProblemType.CONFLICT) {
                issues.add("OPEN_POINT_TYPE_INVALID | problems[" + index + "].type");
            }
        }
        if (!issues.isEmpty()) {
            throw new AiOutputValidationException(
                    "Der KI-Pre-Check verletzt das erwartete Output-Schema.", issues);
        }
    }
}
