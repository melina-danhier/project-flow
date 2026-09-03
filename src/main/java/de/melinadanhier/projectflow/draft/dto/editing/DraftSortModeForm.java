package de.melinadanhier.projectflow.draft.dto.editing;

import de.melinadanhier.projectflow.plancontainer.model.SortMode;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DraftSortModeForm {
    @NotNull private Long lockVersion;
    @NotNull private SortMode sortMode;
}
