package de.melinadanhier.projectflow.planelement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TaskCommentForm {

    @NotBlank(message = "Der Beitrag darf nicht leer sein.")
    @Size(max = 2000, message = "Der Beitrag darf höchstens 2000 Zeichen lang sein.")
    private String content;
}
