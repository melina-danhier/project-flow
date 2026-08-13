package de.melinadanhier.projectflow.plancontainer.template.dto;

import de.melinadanhier.projectflow.plancontainer.model.SortMode;
import de.melinadanhier.projectflow.plancontainer.model.StructureMode;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import de.melinadanhier.projectflow.planelement.dto.SectionDto;
import de.melinadanhier.projectflow.planelement.dto.TaskDetailsDto;
import de.melinadanhier.projectflow.planelement.dto.MilestoneDetailsDto;
import de.melinadanhier.projectflow.planelement.dto.TaskDependencyDto;

@Getter
@Setter
@NoArgsConstructor
public class TemplateDetailsDto {

    private UUID id;
    private String title;
    private String description;
    private StructureMode structureMode;
    private SortMode sortMode;
    private TemplateCategory category;
    private String projectType;
    private Integer recommendedDurationDays;
    private CollaborationMode collaborationMode;
    private boolean active;
    private int version;
    private List<SectionDto> sections = new ArrayList<>();
    private List<TaskDetailsDto> tasks = new ArrayList<>();
    private List<MilestoneDetailsDto> milestones = new ArrayList<>();
    private List<TaskDependencyDto> dependencies = new ArrayList<>();
}
