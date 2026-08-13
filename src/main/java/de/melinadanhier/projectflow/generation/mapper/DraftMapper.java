package de.melinadanhier.projectflow.generation.mapper;

import de.melinadanhier.projectflow.generation.dto.response.DraftPlanElementDto;
import de.melinadanhier.projectflow.generation.dto.response.DraftReviewDto;
import de.melinadanhier.projectflow.generation.dto.response.DraftSectionDto;
import de.melinadanhier.projectflow.generation.dto.response.GenerationStatusDto;
import de.melinadanhier.projectflow.generation.model.DraftPlanElement;
import de.melinadanhier.projectflow.generation.model.DraftSection;
import de.melinadanhier.projectflow.generation.model.PlanDraft;
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
