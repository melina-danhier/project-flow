package de.melinadanhier.projectflow.plancontainer.project.dto;

import de.melinadanhier.projectflow.plancontainer.model.SortMode;
import de.melinadanhier.projectflow.plancontainer.model.StructureMode;
import de.melinadanhier.projectflow.plancontainer.project.model.CreationType;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectLocation;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectStatus;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory;
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
public class ProjectDetailsDto {

    private UUID id;
    private String title;
    private String description;
    private StructureMode structureMode;
    private SortMode sortMode;
    private LocalDate startDate;
    private LocalDate endDate;
    private TemplateCategory category;
    private String projectType;
    private CollaborationMode collaborationMode;
    private CreationType creationType;
    private ProjectStatus status;
    private ProjectLocation location;
    private List<ProjectMemberDto> members = new ArrayList<>();
    private long lockVersion;
}
