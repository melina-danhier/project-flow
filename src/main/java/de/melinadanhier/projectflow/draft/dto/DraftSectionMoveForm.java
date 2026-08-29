package de.melinadanhier.projectflow.draft.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DraftSectionMoveForm {
    @NotNull private Long lockVersion;
    @PositiveOrZero private int targetPosition;
}
