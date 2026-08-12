package com.melina.projectflow.generation.mapper;

import com.melina.projectflow.generation.dto.response.DraftPlanElementDto;
import com.melina.projectflow.generation.dto.response.DraftReviewDto;
import com.melina.projectflow.generation.dto.response.DraftSectionDto;
import com.melina.projectflow.generation.dto.response.GenerationStatusDto;
import com.melina.projectflow.generation.model.DraftPlanElement;
import com.melina.projectflow.generation.model.DraftSection;
import com.melina.projectflow.generation.model.PlanDraft;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DraftMapper {

    @Mapping(target = "draftId", source = "id")
    @Mapping(target = "projectId", source = "project.id")
    GenerationStatusDto toStatusDto(PlanDraft planDraft);

    @Mapping(target = "projectId", source = "project.id")
    DraftReviewDto toReviewDto(PlanDraft planDraft);

    DraftSectionDto toDto(DraftSection draftSection);

    @Mapping(target = "draftSectionId", source = "draftSection.id")
    DraftPlanElementDto toDto(DraftPlanElement draftPlanElement);
}
