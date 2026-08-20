package de.melinadanhier.projectflow.wizard.dto;

import de.melinadanhier.projectflow.wizard.model.ProjectWizardState;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AiProjectDetailsForm {

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
        return form;
    }
}
