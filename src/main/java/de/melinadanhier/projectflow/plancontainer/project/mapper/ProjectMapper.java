package de.melinadanhier.projectflow.plancontainer.project.mapper;

import de.melinadanhier.projectflow.plancontainer.project.dto.ProjectDetailsDto;
import de.melinadanhier.projectflow.plancontainer.project.dto.ProjectMemberDto;
import de.melinadanhier.projectflow.plancontainer.project.dto.ProjectSummaryDto;
import de.melinadanhier.projectflow.plancontainer.project.model.Project;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectMember;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProjectMapper {

    @Mapping(target = "subcategoryOptions", ignore = true)
    ProjectSummaryDto toSummaryDto(Project project);

    @Mapping(target = "members", source = "memberships")
    @Mapping(target = "subcategoryOptions", ignore = true)
    ProjectDetailsDto toDetailsDto(Project project);

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "displayName", source = "user.displayName")
    ProjectMemberDto toMemberDto(ProjectMember projectMember);
}
