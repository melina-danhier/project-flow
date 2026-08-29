package de.melinadanhier.projectflow.plancontainer.project.validation;

import de.melinadanhier.projectflow.common.exception.DomainValidationException;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectClassification;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectSubCategory;
import de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.LinkedHashMap;
import java.util.Map;

public class ProjectClassificationValidator
        implements ConstraintValidator<ValidProjectClassification, ProjectClassification> {
    private boolean requireOtherDescription;

    @Override
    public void initialize(ValidProjectClassification constraint) {
        requireOtherDescription = constraint.requireOtherDescription();
    }

    @Override
    public boolean isValid(ProjectClassification value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        var errors = errors(value.getCategory(), value.getSubcategory(),
                value.getOtherProjectTypeDescription(), requireOtherDescription);
        if (!errors.isEmpty()) {
            context.disableDefaultConstraintViolation();
            errors.forEach((field, message) -> context.buildConstraintViolationWithTemplate(message)
                    .addPropertyNode(field).addConstraintViolation());
        }
        return errors.isEmpty();
    }

    public static void requireValid(TemplateCategory category, ProjectSubCategory subcategory,
                                    String otherDescription) {
        var errors = errors(category, subcategory, otherDescription, true);
        if (!errors.isEmpty()) {
            throw new DomainValidationException(errors.values().iterator().next());
        }
    }

    private static Map<String, String> errors(TemplateCategory category, ProjectSubCategory subcategory,
                                               String otherDescription, boolean requireDescription) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (!ProjectSubCategory.isValidFor(category, subcategory)) {
            errors.put("subcategory", "Bitte wähle eine Unterkategorie der gewählten Oberkategorie oder keine Unterkategorie.");
        }
        if (requireDescription && category == TemplateCategory.OTHER
                && (otherDescription == null || otherDescription.isBlank())) {
            errors.put("otherProjectTypeDescription",
                    "Bitte beschreibe kurz, um welche Art von Projekt es sich handelt.");
        }
        if (otherDescription != null && otherDescription.length() > 100) {
            errors.put("otherProjectTypeDescription", "Die Beschreibung darf höchstens 100 Zeichen lang sein.");
        }
        return errors;
    }
}
