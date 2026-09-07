package de.melinadanhier.projectflow.planelement.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class PlanElementMoveForm {
    @NotNull private Long projectLockVersion;
    private UUID targetSectionId;
    @NotNull @Size(max = 10) private String targetDate;
    @PositiveOrZero private int targetPosition;
}
