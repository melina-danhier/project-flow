package de.melinadanhier.projectflow.wizard.dto;

import de.melinadanhier.projectflow.wizard.model.ProjectWizardState;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
public class AiProjectDetailsForm {

    private Map<String, String> answers = new LinkedHashMap<>();

    @Size(max = 2000, message = "Das Projektziel darf höchstens 2000 Zeichen lang sein.")
    private String projectGoal;

    @Size(max = 2000, message = "Die Rahmenbedingungen dürfen höchstens 2000 Zeichen lang sein.")
    private String constraints;

    @Size(max = 2000, message = "Die weiteren Angaben dürfen höchstens 2000 Zeichen lang sein.")
    private String additionalInformation;

    public static AiProjectDetailsForm from(ProjectWizardState state) {
        AiProjectDetailsForm form = new AiProjectDetailsForm();
        form.setProjectGoal(state.getProjectGoal());
        form.setConstraints(state.getConstraints());
        form.setAdditionalInformation(state.getAdditionalInformation());
        form.setAnswers(new LinkedHashMap<>(state.getProjectSpecificAnswers()));
        return form;
    }
}
