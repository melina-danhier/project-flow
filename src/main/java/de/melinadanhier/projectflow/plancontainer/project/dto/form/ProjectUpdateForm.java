package de.melinadanhier.projectflow.plancontainer.project.dto.form;

import de.melinadanhier.projectflow.common.validation.UpdateValidation;
import de.melinadanhier.projectflow.plancontainer.project.validation.ValidProjectClassification;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@ValidProjectClassification
public class ProjectUpdateForm extends ProjectForm {

    private boolean confirmIndividualConversion;

    @PositiveOrZero
    @NotNull(groups = UpdateValidation.class)
    private Long lockVersion;
}
