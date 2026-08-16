package de.melinadanhier.projectflow.plancontainer.project.dto;

import de.melinadanhier.projectflow.plancontainer.project.model.CreationType;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectLocation;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectStatus;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory;
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
    private TemplateCategory category;
    private String projectType;
    private CollaborationMode collaborationMode;
    private CreationType creationType;
    private ProjectStatus status;
    private ProjectLocation location;
}
