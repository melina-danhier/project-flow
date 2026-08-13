package de.melinadanhier.projectflow.plancontainer.project.dto;

import de.melinadanhier.projectflow.plancontainer.project.model.CreationType;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectLocation;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class ProjectSummaryDto {

    private UUID id;
    private String title;
    private LocalDate startDate;
    private LocalDate endDate;
    private CreationType creationType;
    private ProjectStatus status;
    private ProjectLocation location;
}
