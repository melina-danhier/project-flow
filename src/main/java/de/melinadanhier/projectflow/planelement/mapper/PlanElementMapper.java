package de.melinadanhier.projectflow.planelement.mapper;

import de.melinadanhier.projectflow.planelement.dto.MilestoneDetailsDto;
import de.melinadanhier.projectflow.planelement.dto.PlanElementDto;
import de.melinadanhier.projectflow.planelement.dto.SectionDto;
import de.melinadanhier.projectflow.planelement.dto.TaskDetailsDto;
import de.melinadanhier.projectflow.planelement.model.Milestone;
import de.melinadanhier.projectflow.planelement.model.PlanElement;
import de.melinadanhier.projectflow.planelement.model.PlanSection;
import de.melinadanhier.projectflow.planelement.model.Task;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.UUID;

@Mapper(componentModel = "spring")
public interface PlanElementMapper {

    @Mapping(target = "planContainerId", source = "planContainer.id")
    @Mapping(target = "planSectionId", source = "planSection.id")
    PlanElementDto toDto(PlanElement planElement);

    @Mapping(target = "planContainerId", source = "planContainer.id")
    @Mapping(target = "planSectionId", source = "planSection.id")
    @Mapping(target = "assigneeId", source = "assignee.id")
    @Mapping(target = "assigneeUserId", source = "assignee.user.id")
    @Mapping(target = "assigneeDisplayName", source = "assignee.user.displayName")
    @Mapping(target = "prerequisiteIds", source = "prerequisites")
    @Mapping(target = "predecessors", ignore = true)
    @Mapping(target = "successors", ignore = true)
    @Mapping(target = "availablePrerequisites", ignore = true)
    @Mapping(target = "availableAssignees", ignore = true)
    @Mapping(target = "affectedDependencyCount", ignore = true)
    @Mapping(target = "editable", ignore = true)
    @Mapping(target = "availableSections", ignore = true)
    TaskDetailsDto toDetailsDto(Task task);

    @Mapping(target = "planContainerId", source = "planContainer.id")
    @Mapping(target = "planSectionId", source = "planSection.id")
    @Mapping(target = "editable", ignore = true)
    @Mapping(target = "availableSections", ignore = true)
    MilestoneDetailsDto toDetailsDto(Milestone milestone);

    @Mapping(target = "planContainerId", source = "planContainer.id")
    @Mapping(target = "elements", ignore = true)
    @Mapping(target = "taskCount", ignore = true)
    @Mapping(target = "milestoneCount", ignore = true)
    SectionDto toDto(PlanSection planSection);

    default UUID taskId(Task task) {
        return task == null ? null : task.getId();
    }
}
