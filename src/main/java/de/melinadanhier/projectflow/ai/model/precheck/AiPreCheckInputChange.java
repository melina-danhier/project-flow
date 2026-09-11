package de.melinadanhier.projectflow.ai.model.precheck;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiPreCheckInputChange(
        @NotBlank @Size(max = 200) String field,
        @NotBlank @Size(max = 2000) String previousValue,
        @NotBlank @Size(max = 2000) String newValue
) {
    public AiPreCheckInputChange {
        field = field == null ? null : field.trim();
        previousValue = previousValue == null ? null : previousValue.trim();
        newValue = newValue == null ? null : newValue.trim();
    }

    public String fieldLabel() {
        return switch (field) {
            case "title" -> "Titel";
            case "description" -> "Beschreibung";
            case "startDate" -> "Startdatum";
            case "endDate" -> "Enddatum";
            case "otherProjectTypeDescription" -> "Projektart";
            case "projectGoal" -> "Projektziel / Themenumfang";
            case "constraints" -> "Einschränkungen";
            case "additionalInformation" -> "Zusätzliche Angaben";
            case "durationDays" -> "Dauer";
            case "availableWorkingTime" -> "Verfügbare Arbeitszeit";
            default -> field != null && field.startsWith("projectSpecificAnswers.")
                    ? field.substring("projectSpecificAnswers.".length()) : field;
        };
    }

    public String displayPreviousValue() {
        return displayValue(previousValue);
    }

    public String displayNewValue() {
        return displayValue(newValue);
    }

    private String displayValue(String value) {
        if (value != null && value.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return java.time.LocalDate.parse(value)
                    .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        }
        return value;
    }
}
