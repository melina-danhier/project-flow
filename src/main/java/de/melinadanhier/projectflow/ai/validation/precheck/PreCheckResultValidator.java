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
            if (problem != null && problem.severity() == AiPreCheckSeverity.ERROR
                    && problem.type() != AiPreCheckProblemType.CONFLICT) {
                issues.add("ERROR_TYPE_INVALID | problems[" + index + "].type");
            }
            if (problem != null && problem.severity() == AiPreCheckSeverity.WARNING
                    && problem.type() == AiPreCheckProblemType.CONFLICT) {
                issues.add("OPEN_POINT_TYPE_INVALID | problems[" + index + "].type");
            }
            if (problem != null && problem.severity() == AiPreCheckSeverity.WARNING) {
                validateUserFacingLanguage(problem, index, issues);
            }
            if (problem != null && problem.type() != AiPreCheckProblemType.CRITICAL_ASSUMPTION
                    && !problem.proposedInputChanges().isEmpty()) {
                issues.add("PROPOSED_INPUT_CHANGES_INVALID | problems[" + index + "].proposedInputChanges");
            }
            if (problem != null && problem.type() != AiPreCheckProblemType.CRITICAL_ASSUMPTION
                    && !problem.adjustmentOptions().isEmpty()) {
                issues.add("ADJUSTMENT_OPTIONS_INVALID | problems[" + index + "].adjustmentOptions");
            }
            if (problem != null && problem.proposedInputChanges().stream()
                    .anyMatch(change -> !isSupportedInputField(change.field()))) {
                issues.add("PROPOSED_INPUT_FIELD_INVALID | problems[" + index + "].proposedInputChanges");
            }
            if (problem != null && problem.type() == AiPreCheckProblemType.CRITICAL_ASSUMPTION) {
                if (problem.proposedInputChanges().isEmpty()) {
                    issues.add("PREFERRED_CONCRETE_CHANGE_MISSING | problems[" + index
                            + "].proposedInputChanges");
                }
                if (problem.adjustmentOptions().stream().distinct().count() < 2) {
                    issues.add("GENERAL_ADJUSTMENT_OPTIONS_MISSING | problems[" + index
                            + "].adjustmentOptions");
                }
                validateConcreteChanges(problem, index, issues);
            }
        }
        if (!issues.isEmpty()) {
            throw new AiOutputValidationException(
                    "Der KI-Pre-Check verletzt das erwartete Output-Schema: " + issues, issues);
        }
    }

    private boolean isSupportedInputField(String field) {
        return field != null && (List.of("title", "description", "startDate", "endDate", "otherProjectTypeDescription",
                        "projectGoal", "constraints", "additionalInformation", "durationDays",
                        "availableWorkingTime").contains(field)
                || field.startsWith("projectSpecificAnswers."));
    }

    private void validateConcreteChanges(
            de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckProblem problem,
            int problemIndex, List<String> issues) {
        for (int changeIndex = 0; changeIndex < problem.proposedInputChanges().size(); changeIndex++) {
            var change = problem.proposedInputChanges().get(changeIndex);
            String path = "problems[" + problemIndex + "].proposedInputChanges[" + changeIndex + "]";
            if (change.previousValue() == null || change.newValue() == null
                    || change.previousValue().equalsIgnoreCase(change.newValue())) {
                issues.add("PROPOSED_VALUE_UNCHANGED | " + path);
                continue;
            }
            if (!valueMentioned(problem.acceptedInterpretation(), change.previousValue())
                    || !valueMentioned(problem.acceptedInterpretation(), change.newValue())) {
                issues.add("PREFERRED_CHANGE_VALUES_MISSING | " + path);
            }
            String normalized = change.newValue().toLowerCase(java.util.Locale.GERMAN);
            if (List.of("eine auswahl", "auswahl der", "längerer zeitraum", "zeitraum erweitern",
                            "umfang reduzieren", "spätere lernphase", "spaetere lernphase",
                            "zentrale themen", "einige themen", "relevante themen")
                    .stream().anyMatch(normalized::contains)) {
                issues.add("PROPOSED_VALUE_TOO_VAGUE | " + path);
            }
            if ("durationDays".equals(change.field()) && !isPositiveInteger(change.newValue())) {
                issues.add("PROPOSED_DURATION_INVALID | " + path);
            }
            if (("startDate".equals(change.field()) || "endDate".equals(change.field()))
                    && !isIsoDate(change.newValue())) {
                issues.add("PROPOSED_DATE_INVALID | " + path);
            }
            if ("availableWorkingTime".equals(change.field())
                    && !change.newValue().matches(".*\\d+(?:[.,]\\d+)?\\s*(?:h|stunde|stunden).*")) {
                issues.add("PROPOSED_WORKING_TIME_INVALID | " + path);
            }
            String proposalText = (problem.message() + " " + problem.suggestedUserAction() + " "
                    + problem.acceptedInterpretation()).toLowerCase(java.util.Locale.GERMAN);
            boolean scopeReduction = proposalText.contains("reduzier") || proposalText.contains("begrenz")
                    || proposalText.contains("teilmenge") || proposalText.contains("auswahl");
            boolean scopeField = "projectGoal".equals(change.field())
                    || change.field().toLowerCase(java.util.Locale.ROOT).contains("scope")
                    || change.field().toLowerCase(java.util.Locale.GERMAN).contains("themen");
            if (scopeReduction && scopeField && !change.newValue().matches(".*[,;•].*")) {
                issues.add("PROPOSED_SCOPE_NOT_EXPLICIT | " + path);
            }
        }
    }

    private boolean valueMentioned(String text, String value) {
        if (text == null || value == null) {
            return false;
        }
        String normalizedText = text.toLowerCase(java.util.Locale.GERMAN);
        if (normalizedText.contains(value.toLowerCase(java.util.Locale.GERMAN))) {
            return true;
        }
        if (value.matches("\\d{4}-\\d{2}-\\d{2}")) {
            String displayDate = java.time.LocalDate.parse(value)
                    .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            return normalizedText.contains(displayDate);
        }
        return false;
    }

    private void validateUserFacingLanguage(
            de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckProblem problem,
            int index, List<String> issues) {
        String text = java.util.stream.Stream.of(problem.message(), problem.suggestedUserAction(),
                        problem.acceptedInterpretation())
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.joining(" "));
        String normalized = text.toLowerCase(java.util.Locale.GERMAN);
        if (List.of("offensichtliches missverhältnis", "planbar", "lernumfang", "einzelmodus")
                .stream().anyMatch(normalized::contains)) {
            issues.add("USER_FACING_LANGUAGE_TOO_ABSTRACT | problems[" + index + "]");
        }
        if (text.matches(".*\\b\\d{4}-\\d{2}-\\d{2}\\b.*")) {
            issues.add("USER_FACING_DATE_FORMAT_INVALID | problems[" + index + "]");
        }
    }

    private boolean isPositiveInteger(String value) {
        try {
            return Integer.parseInt(value) > 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private boolean isIsoDate(String value) {
        try {
            java.time.LocalDate.parse(value);
            return true;
        } catch (java.time.format.DateTimeParseException exception) {
            return false;
        }
    }
}
