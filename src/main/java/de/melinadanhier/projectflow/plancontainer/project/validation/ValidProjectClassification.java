package de.melinadanhier.projectflow.plancontainer.project.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ProjectClassificationValidator.class)
public @interface ValidProjectClassification {
    String message() default "Bitte prüfe die Projektkategorie.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    boolean requireOtherDescription() default true;
}
