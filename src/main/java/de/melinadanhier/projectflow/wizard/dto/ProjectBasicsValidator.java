package de.melinadanhier.projectflow.wizard.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ProjectBasicsValidator implements ConstraintValidator<ValidProjectBasics, ProjectBasicsForm> {

    @Override
    public boolean isValid(ProjectBasicsForm form, ConstraintValidatorContext context) {
        if (form == null) {
            return true;
        }

        if (form.getTimeFrameType() == null) {
            return true;
        }

        return switch (form.getTimeFrameType()) {
            case START_AND_END -> validateStartAndEnd(form, context);
            case START_AND_DURATION -> validateStartAndDuration(form, context);
            case END_AND_DURATION -> validateEndAndDuration(form, context);
            case NONE -> validateNoTimeInformation(form, context);
        };
    }

    private boolean validateStartAndEnd(ProjectBasicsForm form, ConstraintValidatorContext context) {
        boolean valid = true;
        if (form.getStartDate() == null) {
            addViolation(context, "startDate", "Bitte gib ein Startdatum an.");
            valid = false;
        }
        if (form.getEndDate() == null) {
            addViolation(context, "endDate", "Bitte gib ein Enddatum an.");
            valid = false;
        }
        if (form.getDurationDays() != null) {
            addViolation(context, "timeFrameType", "Zu Start und Ende darf keine Dauer angegeben werden.");
            valid = false;
        }
        if (form.getStartDate() != null && form.getEndDate() != null
                && form.getEndDate().isBefore(form.getStartDate())) {
            addViolation(context, "endDate", "Das Enddatum darf nicht vor dem Startdatum liegen.");
            valid = false;
        }
        return valid;
    }

    private boolean validateStartAndDuration(ProjectBasicsForm form, ConstraintValidatorContext context) {
        boolean valid = true;
        if (form.getStartDate() == null) {
            addViolation(context, "startDate", "Bitte gib ein Startdatum an.");
            valid = false;
        }
        if (form.getDurationDays() == null) {
            addViolation(context, "durationDays", "Bitte gib die Dauer in Tagen an.");
            valid = false;
        }
        if (form.getEndDate() != null) {
            addViolation(context, "timeFrameType", "Bitte gib für diesen Zeitmodus kein Enddatum an.");
            valid = false;
        }
        return valid;
    }

    private boolean validateEndAndDuration(ProjectBasicsForm form, ConstraintValidatorContext context) {
        boolean valid = true;
        if (form.getEndDate() == null) {
            addViolation(context, "endDate", "Bitte gib ein Enddatum an.");
            valid = false;
        }
        if (form.getDurationDays() == null) {
            addViolation(context, "durationDays", "Bitte gib die Dauer in Tagen an.");
            valid = false;
        }
        if (form.getStartDate() != null) {
            addViolation(context, "timeFrameType", "Bitte gib für diesen Zeitmodus kein Startdatum an.");
            valid = false;
        }
        return valid;
    }

    private boolean validateNoTimeInformation(ProjectBasicsForm form, ConstraintValidatorContext context) {
        if (form.getStartDate() == null && form.getEndDate() == null && form.getDurationDays() == null) {
            return true;
        }
        addViolation(context, "timeFrameType", "Wähle einen passenden Zeitmodus für deine Angaben.");
        return false;
    }

    private void addViolation(ConstraintValidatorContext context, String field, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(field)
                .addConstraintViolation();
    }
}
