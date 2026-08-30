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
import java.util.HashSet;
import java.util.Locale;

import static de.melinadanhier.projectflow.ai.validation.generation.GenerationValidationCode.*;

@Component
@RequiredArgsConstructor
public class GenerationResponseValidator {

    private final Validator validator;

    public GenerationValidationResult validate(GeneratedPlanResponse response, AiGenerationRequest request) {
        List<GenerationValidationIssue> issues = new ArrayList<>();
        AiWizardSnapshot snapshot = wizardSnapshot(request, issues);
        issues.addAll(validatePlan(response, snapshot).issues());
        return new GenerationValidationResult(issues);
    }

    public GenerationValidationResult validatePlan(GeneratedPlanResponse response, AiWizardSnapshot snapshot) {
        List<GenerationValidationIssue> issues = new ArrayList<>();
        if (response == null) {
            issues.add(new GenerationValidationIssue(RESPONSE_MISSING));
            return new GenerationValidationResult(issues);
        }
        validateBeanConstraints(response, issues);
        validateCriticalAssumptions(response, issues);
        new GenerationStructureValidator(snapshot, issues).validate(response);
        return new GenerationValidationResult(issues);
    }

    private void validateCriticalAssumptions(GeneratedPlanResponse response,
                                             List<GenerationValidationIssue> issues) {
        if (response.criticalAssumptions() == null) {
            issues.add(new GenerationValidationIssue(CRITICAL_ASSUMPTIONS_MISSING, "criticalAssumptions"));
            return;
        }
        var normalized = new HashSet<String>();
        for (int index = 0; index < response.criticalAssumptions().size(); index++) {
            var assumption = response.criticalAssumptions().get(index);
            String path = "criticalAssumptions[" + index + "].statement";
            if (assumption == null || assumption.statement() == null || assumption.statement().isBlank()) {
                issues.add(new GenerationValidationIssue(CRITICAL_ASSUMPTION_INVALID, path));
                continue;
            }
            String key = assumption.statement().strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
            if (!normalized.add(key)) {
                issues.add(new GenerationValidationIssue(CRITICAL_ASSUMPTION_DUPLICATE, path));
            }
        }
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
            issues.add(new GenerationValidationIssue(REQUEST_MISSING));
            return null;
        }
        if (request.confirmedWizardData() == null) {
            issues.add(new GenerationValidationIssue(WIZARD_DATA_MISSING));
            return null;
        }
        return request.confirmedWizardData();
    }

}
