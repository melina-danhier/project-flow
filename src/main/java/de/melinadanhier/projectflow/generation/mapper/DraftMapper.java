package de.melinadanhier.projectflow.generation.mapper;

import de.melinadanhier.projectflow.generation.dto.response.DraftPlanElementDto;
import de.melinadanhier.projectflow.generation.dto.response.DraftReviewDto;
import de.melinadanhier.projectflow.generation.dto.response.DraftSectionDto;
import de.melinadanhier.projectflow.generation.dto.response.GenerationStatusDto;
import de.melinadanhier.projectflow.generation.model.DraftPlanElement;
import de.melinadanhier.projectflow.generation.model.DraftSection;
import de.melinadanhier.projectflow.generation.model.PlanDraft;
import de.melinadanhier.projectflow.generation.model.DraftTask;
import de.melinadanhier.projectflow.generation.model.DraftMilestone;
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

    default DraftPlanElementDto toDto(DraftPlanElement source) {
        DraftPlanElementDto target = new DraftPlanElementDto();
        target.setId(source.getId());
        target.setDraftSectionId(source.getDraftSection() == null ? null : source.getDraftSection().getId());
        target.setTitle(source.getTitle());
        target.setDescription(source.getDescription());
        target.setSortOrder(source.getSortOrder());
        target.setReviewStatus(source.getReviewStatus());
        target.setUserModified(source.isUserModified());
        target.setHasCriticalAssumption(source.isHasCriticalAssumption());
        target.setCriticalAssumption(source.getCriticalAssumption());
        if (source instanceof DraftTask task) {
            target.setType("TASK");
            target.setStartDate(task.getStartDate());
            target.setDueDate(task.getDueDate());
            target.setEstimatedHours(task.getEstimatedHours());
        } else if (source instanceof DraftMilestone milestone) {
            target.setType("MILESTONE");
            target.setDueDate(milestone.getDueDate());
        }
        return target;
    }
}
