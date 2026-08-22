package de.melinadanhier.projectflow.generation.dto.response;

import de.melinadanhier.projectflow.generation.model.ReviewStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class DraftPlanElementDto {

    private UUID id;
    private UUID draftSectionId;
    private String title;
    private String description;
    private int sortOrder;
    private ReviewStatus reviewStatus;
    private boolean userModified;
    private boolean hasCriticalAssumption;
    private String criticalAssumption;
    private String type;
    private LocalDate startDate;
    private LocalDate dueDate;
    private Integer estimatedHours;
}
