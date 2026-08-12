package com.melina.projectflow.plancontainer.project.mapper;

import com.melina.projectflow.plancontainer.project.dto.ProjectDetailsDto;
import com.melina.projectflow.plancontainer.project.dto.ProjectMemberDto;
import com.melina.projectflow.plancontainer.project.dto.ProjectSummaryDto;
import com.melina.projectflow.plancontainer.project.model.Project;
import com.melina.projectflow.plancontainer.project.model.ProjectMember;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProjectMapper {

    ProjectSummaryDto toSummaryDto(Project project);

    @Mapping(target = "members", source = "memberships")
    ProjectDetailsDto toDetailsDto(Project project);

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "displayName", source = "user.displayName")
    ProjectMemberDto toMemberDto(ProjectMember projectMember);
}
