package de.melinadanhier.projectflow.planelement.dto;

import de.melinadanhier.projectflow.planelement.model.TaskPriority;
import de.melinadanhier.projectflow.planelement.model.TaskStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class PlanElementViewDto {

    private UUID id;
    private PlanElementType type;
    private String title;
    private String description;
    private UUID planSectionId;
    private int sortOrder;
    private LocalDate relevantDate;
    private TaskStatus taskStatus;
    private TaskPriority taskPriority;
    private boolean milestoneCompleted;
}
