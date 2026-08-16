package de.melinadanhier.projectflow.wizard.dto;

import de.melinadanhier.projectflow.plancontainer.project.model.CreationType;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ProjectCreationMethodForm {

    @NotNull(message = "Bitte wähle aus, wie du dein Projekt erstellen möchtest.")
    private CreationType creationType;
}
