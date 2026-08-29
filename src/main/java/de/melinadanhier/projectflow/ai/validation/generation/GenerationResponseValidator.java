package de.melinadanhier.projectflow.ai.validation.generation;

import de.melinadanhier.projectflow.ai.model.generation.AiGenerationRequest;
import de.melinadanhier.projectflow.ai.model.generation.GeneratedPlanResponse;
import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static de.melinadanhier.projectflow.ai.validation.generation.GenerationValidationCode.*;

@Component
@RequiredArgsConstructor
public class GenerationResponseValidator {

    private List<GenerationValidationIssue> issues;
    private final Validator validator;

    public GenerationValidationResult validate(GeneratedPlanResponse response, AiGenerationRequest request) {
        issues = new ArrayList<>();
        AiWizardSnapshot snapshot = wizardSnapshot(request, issues);
        issues.addAll(validatePlan(response, snapshot).issues());
        return new GenerationValidationResult(issues);
    }

    public GenerationValidationResult validatePlan(GeneratedPlanResponse response, AiWizardSnapshot snapshot) {
        List<GenerationValidationIssue> issues = new ArrayList<>();
        if (response == null) {
            addIssue(RESPONSE_MISSING);
            return new GenerationValidationResult(issues);
        }
        validateBeanConstraints(response, issues);
        new GenerationStructureValidator(snapshot, issues).validate(response);
        return new GenerationValidationResult(issues);
    }

    private void validateBeanConstraints(GeneratedPlanResponse response, List<GenerationValidationIssue> issues) {
        validator.validate(response).stream()
                .map(this::issueFromViolation)
                .forEach(issues::add);
    }

    private GenerationValidationIssue issueFromViolation(ConstraintViolation<GeneratedPlanResponse> violation) {
        return new GenerationValidationIssue(
                BEAN_VALIDATION_FAILED,
                violation.getPropertyPath().toString(),
                violation.getPropertyPath() + ": " + violation.getMessage()
        );
    }

    private AiWizardSnapshot wizardSnapshot(AiGenerationRequest request, List<GenerationValidationIssue> issues) {
        if (request == null) {
            addIssue(REQUEST_MISSING);
            return null;
        }
        if (request.confirmedWizardData() == null) {
            addIssue(WIZARD_DATA_MISSING);
            return null;
        }
        return request.confirmedWizardData();
    }

    private void addIssue(GenerationValidationCode code) {
        issues.add(new GenerationValidationIssue(code));
    }
}
