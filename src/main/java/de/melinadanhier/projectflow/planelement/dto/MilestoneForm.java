package de.melinadanhier.projectflow.planelement.dto;

import de.melinadanhier.projectflow.common.validation.UpdateValidation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class MilestoneForm {

    private UUID planSectionId;

    @NotBlank
    @Size(max = 100)
    private String title;

    @Size(max = 2000)
    private String description;

    @PositiveOrZero
    private Integer sortOrder;

    private LocalDate dueDate;

    private boolean completed;

    @PositiveOrZero
    @NotNull(groups = UpdateValidation.class)
    private Long lockVersion;
}
