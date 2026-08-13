package de.melinadanhier.projectflow.plancontainer.project.dto;

import de.melinadanhier.projectflow.planelement.dto.MilestoneDetailsDto;
import de.melinadanhier.projectflow.planelement.dto.SectionDto;
import de.melinadanhier.projectflow.planelement.dto.TaskDependencyDto;
import de.melinadanhier.projectflow.planelement.dto.TaskDetailsDto;
import de.melinadanhier.projectflow.planelement.dto.PlanElementViewDto;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class ProjectPlanViewDto {

    private ProjectDetailsDto project;
    private List<SectionDto> sections = new ArrayList<>();
    private List<TaskDetailsDto> tasks = new ArrayList<>();
    private List<MilestoneDetailsDto> milestones = new ArrayList<>();
    private List<TaskDependencyDto> dependencies = new ArrayList<>();
    private List<ProjectMemberDto> activeMembers = new ArrayList<>();
    private boolean editable;
    private boolean owner;
    private List<PlanElementViewDto> unsectionedElements = new ArrayList<>();
}
