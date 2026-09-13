package de.melinadanhier.projectflow.plancontainer.project.validation;

import de.melinadanhier.projectflow.common.exception.DomainValidationException;
import de.melinadanhier.projectflow.plancontainer.project.model.classification.ProjectClassification;
import de.melinadanhier.projectflow.plancontainer.project.model.classification.ProjectSubCategory;
import de.melinadanhier.projectflow.plancontainer.template.model.ProjectCategory;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.LinkedHashMap;
import java.util.Map;

public class ProjectClassificationValidator
        implements ConstraintValidator<ValidProjectClassification, ProjectClassification> {

    @Override
    public boolean isValid(ProjectClassification value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        var errors = errors(value.getCategory(), value.getSubcategory(),
                value.getOtherProjectTypeDescription());
        if (!errors.isEmpty()) {
            context.disableDefaultConstraintViolation();
            errors.forEach((field, message) -> context.buildConstraintViolationWithTemplate(message)
                    .addPropertyNode(field).addConstraintViolation());
        }
        return errors.isEmpty();
    }

    public static void requireValid(ProjectCategory category, ProjectSubCategory subcategory,
                                    String otherDescription) {
        var errors = errors(category, subcategory, otherDescription);
        if (!errors.isEmpty()) {
            throw new DomainValidationException(errors.values().iterator().next());
        }
    }

    private static Map<String, String> errors(ProjectCategory category, ProjectSubCategory subcategory,
                                               String otherDescription) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (!ProjectSubCategory.isValidFor(category, subcategory)) {
            errors.put("subcategory", "Bitte wähle eine Unterkategorie der gewählten Oberkategorie oder keine Unterkategorie.");
        }
        if (otherDescription != null && otherDescription.length() > 100) {
            errors.put("otherProjectTypeDescription", "Die Beschreibung darf höchstens 100 Zeichen lang sein.");
        }
        return errors;
    }
}
