package de.melinadanhier.projectflow.planelement.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PlanSectionMoveForm {
    @NotNull private Long projectLockVersion;
    @PositiveOrZero private int targetPosition;
}
