package de.melinadanhier.projectflow.plancontainer.template.mapper;

import de.melinadanhier.projectflow.plancontainer.template.dto.TemplateDetailsDto;
import de.melinadanhier.projectflow.plancontainer.template.dto.TemplateSummaryDto;
import de.melinadanhier.projectflow.plancontainer.template.model.Template;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TemplateMapper {

    @Mapping(target = "subcategoryOptions", ignore = true)
    TemplateSummaryDto toSummaryDto(Template template);

    @Mapping(target = "sections", ignore = true)
    @Mapping(target = "subcategoryOptions", ignore = true)
    @Mapping(target = "tasks", ignore = true)
    @Mapping(target = "milestones", ignore = true)
    @Mapping(target = "dependencies", ignore = true)
    TemplateDetailsDto toDetailsDto(Template template);
}
