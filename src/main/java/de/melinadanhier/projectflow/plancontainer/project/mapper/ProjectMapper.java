package de.melinadanhier.projectflow.plancontainer.project.mapper;

import de.melinadanhier.projectflow.plancontainer.project.dto.view.ProjectDetailsDto;
import de.melinadanhier.projectflow.plancontainer.project.dto.view.ProjectMemberDto;
import de.melinadanhier.projectflow.plancontainer.project.dto.view.ProjectSummaryDto;
import de.melinadanhier.projectflow.plancontainer.project.model.Project;
import de.melinadanhier.projectflow.plancontainer.project.model.membership.ProjectMember;
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
