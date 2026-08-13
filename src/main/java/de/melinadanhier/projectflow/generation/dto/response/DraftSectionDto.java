package de.melinadanhier.projectflow.generation.dto.response;

import de.melinadanhier.projectflow.generation.model.ReviewStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class DraftSectionDto {

    private UUID id;
    private String title;
    private String description;
    private LocalDate startDate;
    private LocalDate endDate;
    private int sortOrder;
    private ReviewStatus reviewStatus;
    private boolean userModified;
    private boolean hasCriticalAssumption;
    private List<DraftPlanElementDto> elements = new ArrayList<>();
}
