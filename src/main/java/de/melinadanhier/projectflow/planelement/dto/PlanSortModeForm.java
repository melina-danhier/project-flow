package de.melinadanhier.projectflow.planelement.dto;

import de.melinadanhier.projectflow.plancontainer.model.SortMode;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PlanSortModeForm {
    @NotNull private Long projectLockVersion;
    @NotNull private SortMode sortMode;
}
