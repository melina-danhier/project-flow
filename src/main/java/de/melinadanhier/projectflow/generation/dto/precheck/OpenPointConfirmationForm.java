package de.melinadanhier.projectflow.generation.dto.precheck;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class OpenPointConfirmationForm {

    @Size(max = 1000, message = "Die Änderung oder Ergänzung darf höchstens 1000 Zeichen lang sein.")
    private String planningContext;
}
