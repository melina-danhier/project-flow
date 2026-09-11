package de.melinadanhier.projectflow.wizard.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ProjectBasicsValidator implements ConstraintValidator<ValidProjectBasics, ProjectBasicsForm> {

    @Override
    public boolean isValid(ProjectBasicsForm form, ConstraintValidatorContext context) {
        if (form == null) {
            return true;
        }

        boolean valid = true;
        if (form.getStartDate() != null && form.getEndDate() != null
                && form.getEndDate().isBefore(form.getStartDate())) {
            addViolation(context, "endDate", "Das Enddatum darf nicht vor dem Startdatum liegen.");
            valid = false;
        }
        if (form.getStartDate() != null && form.getEndDate() != null && form.getDurationDays() != null) {
            long calendarDays = java.time.temporal.ChronoUnit.DAYS.between(
                    form.getStartDate(), form.getEndDate()) + 1;
            if (calendarDays != form.getDurationDays()) {
                addViolation(context, "durationDays",
                        "Die Dauer passt nicht zum angegebenen Start- und Enddatum.");
                valid = false;
            }
        }
        return valid;
    }

    private void addViolation(ConstraintValidatorContext context, String field, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(field)
                .addConstraintViolation();
    }
}
