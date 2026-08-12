package com.melina.projectflow.plancontainer.template.mapper;

import com.melina.projectflow.plancontainer.template.dto.TemplateDetailsDto;
import com.melina.projectflow.plancontainer.template.dto.TemplateSummaryDto;
import com.melina.projectflow.plancontainer.template.model.Template;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TemplateMapper {

    TemplateSummaryDto toSummaryDto(Template template);

    TemplateDetailsDto toDetailsDto(Template template);
}
