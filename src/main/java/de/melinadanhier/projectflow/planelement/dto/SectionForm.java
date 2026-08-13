package de.melinadanhier.projectflow.planelement.dto;

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
public class SectionForm {

    @NotBlank
    @Size(max = 100)
    private String title;

    @Size(max = 2000)
    private String description;

    private LocalDate startDate;
    private LocalDate endDate;

    @PositiveOrZero
    private Integer sortOrder;

    @PositiveOrZero
    private Long lockVersion;

}
