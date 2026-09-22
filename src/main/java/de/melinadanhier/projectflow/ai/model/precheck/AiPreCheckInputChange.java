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
        return PreCheckFieldLabelResolver.resolveLabel(field);
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
