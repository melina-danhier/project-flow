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
        var errors = errors(value.getCategory(), value.getSubcategory());
        if (!errors.isEmpty()) {
            context.disableDefaultConstraintViolation();
            errors.forEach((field, message) -> context.buildConstraintViolationWithTemplate(message)
                    .addPropertyNode(field).addConstraintViolation());
        }
        return errors.isEmpty();
    }

    public static void requireValid(ProjectCategory category, ProjectSubCategory subcategory) {
        var errors = errors(category, subcategory);
        if (!errors.isEmpty()) {
            throw new DomainValidationException(errors.values().iterator().next());
        }
    }

    public static void requireValid(ProjectCategory category, ProjectSubCategory subcategory,
                                    String otherDescription) {
        requireValid(category, subcategory);
    }

    private static Map<String, String> errors(ProjectCategory category, ProjectSubCategory subcategory) {
        Map<String, String> errors = new LinkedHashMap<>();
        ProjectSubCategory effective = (subcategory == null && category != null)
                ? ProjectSubCategory.defaultForCategory(category)
                : subcategory;
        if (!ProjectSubCategory.isValidFor(category, effective)) {
            errors.put("subcategory", ProjectSubCategory.forCategory(category).isEmpty()
                    ? "Für diese Oberkategorie ist keine Unterkategorie vorgesehen."
                    : "Bitte wähle eine Unterkategorie der gewählten Oberkategorie.");
        }
        return errors;
    }
}
