package de.melinadanhier.projectflow.plancontainer.project.dto.view;

import de.melinadanhier.projectflow.plancontainer.project.model.classification.ProjectClassification;
import de.melinadanhier.projectflow.plancontainer.project.model.classification.ProjectSubCategory;
import de.melinadanhier.projectflow.plancontainer.project.model.collaboration.ProjectCollaboration;
import de.melinadanhier.projectflow.plancontainer.model.SortMode;
import de.melinadanhier.projectflow.plancontainer.model.StructureMode;
import de.melinadanhier.projectflow.plancontainer.project.model.lifecycle.CreationType;
import de.melinadanhier.projectflow.plancontainer.project.model.lifecycle.ProjectLocation;
import de.melinadanhier.projectflow.plancontainer.project.model.lifecycle.ProjectStatus;
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
public class ProjectDetailsDto implements ProjectClassification, ProjectCollaboration {

    private UUID id;
    private String title;
    private String description;
    private StructureMode structureMode;
    private SortMode sortMode;
    private LocalDate startDate;
    private LocalDate endDate;
    private TemplateCategory category;
    private String otherProjectTypeDescription;

    private ProjectSubCategory subcategory;
    private CollaborationMode collaborationMode;

    private CreationType creationType;
    private ProjectStatus status;
    private ProjectLocation location;
    private List<ProjectMemberDto> members = new ArrayList<>();
    private long lockVersion;
}
