package de.melinadanhier.projectflow.draft.dto;

import de.melinadanhier.projectflow.ai.model.generation.GeneratedElementOrigin;
import de.melinadanhier.projectflow.draft.model.DraftReviewStatus;
import de.melinadanhier.projectflow.planelement.model.TaskPriority;
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
    private DraftReviewStatus reviewStatus;
    private GeneratedElementOrigin aiOrigin;
    private boolean userModified;
    private boolean hasCriticalAssumption;
    private String criticalAssumption;
    private String type;
    private LocalDate startDate;
    private LocalDate dueDate;
    private Integer estimatedHours;
    private TaskPriority priority;
}
