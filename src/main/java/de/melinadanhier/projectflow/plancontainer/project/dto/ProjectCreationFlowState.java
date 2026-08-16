package de.melinadanhier.projectflow.plancontainer.project.dto;

import de.melinadanhier.projectflow.plancontainer.model.SortMode;
import de.melinadanhier.projectflow.plancontainer.model.StructureMode;
import de.melinadanhier.projectflow.plancontainer.project.model.CreationType;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Temporärer, entity-freier Zustand für die fortgesetzte Projekterstellung.
 * Template-Auswahl und KI-spezifische Angaben können später als getrennte
 * Bestandteile ergänzt werden.
 */
@Getter
@Setter
@NoArgsConstructor
public class ProjectCreationFlowState implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private UUID userId;
    private String title;
    private String description;
    private TemplateCategory category = TemplateCategory.OTHER;
    private String projectType;
    private CollaborationMode collaborationMode;
    private CreationType creationType;
    private LocalDate startDate;
    private LocalDate endDate;
    private ProjectTimeFrameType timeFrameType = ProjectTimeFrameType.NONE;
    private Integer durationDays;
    private StructureMode structureMode;
    private SortMode sortMode;

    public static ProjectCreationFlowState from(ProjectCreateForm form, UUID userId) {
        ProjectCreationFlowState state = new ProjectCreationFlowState();
        state.setUserId(userId);
        state.setTitle(form.getTitle().trim());
        state.setDescription(form.getDescription());
        state.setCategory(form.getCategory());
        state.setProjectType(form.getProjectType());
        state.setCollaborationMode(form.getCollaborationMode());
        state.setCreationType(form.getCreationType());
        state.setStartDate(form.getStartDate());
        state.setEndDate(form.getEndDate());
        state.setTimeFrameType(determineTimeFrameType(form));
        state.setStructureMode(form.getStructureMode());
        state.setSortMode(form.getSortMode());
        return state;
    }

    private static ProjectTimeFrameType determineTimeFrameType(ProjectCreateForm form) {
        if (form.getStartDate() != null && form.getEndDate() != null) {
            return ProjectTimeFrameType.START_AND_END;
        }
        if (form.getStartDate() != null) {
            return ProjectTimeFrameType.START_AND_DURATION;
        }
        if (form.getEndDate() != null) {
            return ProjectTimeFrameType.END_AND_DURATION;
        }
        return ProjectTimeFrameType.NONE;
    }
}
