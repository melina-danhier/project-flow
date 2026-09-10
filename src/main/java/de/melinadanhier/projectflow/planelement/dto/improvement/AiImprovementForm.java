package de.melinadanhier.projectflow.planelement.dto.improvement;

import de.melinadanhier.projectflow.ai.model.improvement.AiFeedbackType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AiImprovementForm {
    public static final int MAX_COMMENT_LENGTH = 500;

    @NotNull(message = "Bitte wähle aus, wie das Element verbessert werden soll.")
    private AiFeedbackType feedbackType;

    @Size(max = MAX_COMMENT_LENGTH, message = "Der Kommentar darf höchstens 500 Zeichen lang sein.")
    private String comment;
}
