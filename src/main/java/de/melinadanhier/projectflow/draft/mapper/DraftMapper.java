package de.melinadanhier.projectflow.draft.mapper;

import de.melinadanhier.projectflow.draft.model.*;
import de.melinadanhier.projectflow.draft.dto.DraftPlanElementDto;
import de.melinadanhier.projectflow.draft.dto.DraftReviewDto;
import de.melinadanhier.projectflow.draft.dto.DraftSectionDto;
import de.melinadanhier.projectflow.generation.dto.GenerationStatusDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DraftMapper {

    @Mapping(target = "projectId", source = "project.id")
    @Mapping(target = "projectTitle", source = "project.title")
    @Mapping(target = "projectStartDate", source = "project.startDate")
    @Mapping(target = "projectEndDate", source = "project.endDate")
    @Mapping(target = "categoryLabel", ignore = true)
    @Mapping(target = "uncheckedCriticalTasks", ignore = true)
    @Mapping(target = "activeReviewStatus", ignore = true)
    @Mapping(target = "criticalAssumptionsOnly", ignore = true)
    @Mapping(target = "reviewedElementCount", ignore = true)
    @Mapping(target = "totalElementCount", ignore = true)
    @Mapping(target = "pendingElementCount", ignore = true)
    @Mapping(target = "unsectionedElements", ignore = true)
    DraftReviewDto toReviewDto(DraftPlan draftPlan);

    DraftSectionDto toDto(DraftSection draftSection);

    default DraftPlanElementDto toDto(DraftPlanElement source) {
        DraftPlanElementDto target = new DraftPlanElementDto();
        target.setId(source.getId());
        target.setDraftSectionId(source.getDraftSection() == null ? null : source.getDraftSection().getId());
        target.setTitle(source.getTitle());
        target.setDescription(source.getDescription());
        target.setSortOrder(source.getSortOrder());
        target.setReviewStatus(source.getReviewStatus());
        target.setOrigin(source.getOrigin());
        target.setHasCriticalAssumption(source.isHasCriticalAssumption());
        target.setCriticalAssumption(source.getCriticalAssumption());
        if (source instanceof DraftTask task) {
            target.setPriority(task.getPriority());
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
