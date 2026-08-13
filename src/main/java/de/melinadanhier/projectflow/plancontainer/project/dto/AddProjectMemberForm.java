package de.melinadanhier.projectflow.plancontainer.project.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AddProjectMemberForm {

    @NotBlank
    @Email
    @Size(max = 254)
    private String email;
}
