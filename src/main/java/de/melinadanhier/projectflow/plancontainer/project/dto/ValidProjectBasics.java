package de.melinadanhier.projectflow.plancontainer.project.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ProjectBasicsValidator.class)
public @interface ValidProjectBasics {

    String message() default "Die Projektangaben sind nicht vollständig oder widersprüchlich.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
