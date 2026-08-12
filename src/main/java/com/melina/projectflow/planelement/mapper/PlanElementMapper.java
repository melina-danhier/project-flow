package com.melina.projectflow.planelement.mapper;

import com.melina.projectflow.planelement.dto.MilestoneDetailsDto;
import com.melina.projectflow.planelement.dto.PlanElementDto;
import com.melina.projectflow.planelement.dto.SectionDto;
import com.melina.projectflow.planelement.dto.TaskDetailsDto;
import com.melina.projectflow.planelement.model.Milestone;
import com.melina.projectflow.planelement.model.PlanElement;
import com.melina.projectflow.planelement.model.PlanSection;
import com.melina.projectflow.planelement.model.Task;
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
    @Mapping(target = "prerequisiteIds", source = "prerequisites")
    TaskDetailsDto toDetailsDto(Task task);

    @Mapping(target = "planContainerId", source = "planContainer.id")
    @Mapping(target = "planSectionId", source = "planSection.id")
    MilestoneDetailsDto toDetailsDto(Milestone milestone);

    @Mapping(target = "planContainerId", source = "planContainer.id")
    SectionDto toDto(PlanSection planSection);

    default UUID taskId(Task task) {
        return task == null ? null : task.getId();
    }
}
