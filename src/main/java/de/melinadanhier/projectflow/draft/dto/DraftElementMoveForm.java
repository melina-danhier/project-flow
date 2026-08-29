package de.melinadanhier.projectflow.draft.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class DraftElementMoveForm {
    @NotNull private Long lockVersion;
    private UUID targetSectionId;
    @PositiveOrZero private int targetPosition;
}
