package de.melinadanhier.projectflow.feedback.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class AiFeedbackForm {
    @NotNull(message = "Bitte wähle eine Bewertung aus.")
    @Min(value = 1, message = "Bitte wähle eine Bewertung aus.")
    @Max(value = 5, message = "Bitte wähle eine gültige Bewertung aus.")
    private Integer rating;
    @Size(max = 2000, message = "Deine Begründung darf höchstens 2000 Zeichen lang sein.")
    private String comment;
}
