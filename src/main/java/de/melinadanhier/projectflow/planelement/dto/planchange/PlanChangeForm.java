package de.melinadanhier.projectflow.planelement.dto.planchange;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class PlanChangeForm {
    public static final int MAX_LENGTH = 1000;
    @NotBlank(message = "Bitte beschreibe die gewünschte Änderung.")
    @Size(max = MAX_LENGTH, message = "Der Änderungswunsch darf höchstens 1000 Zeichen lang sein.")
    private String changeRequest;
}
