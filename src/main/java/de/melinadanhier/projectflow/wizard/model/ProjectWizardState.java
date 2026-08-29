package de.melinadanhier.projectflow.wizard.model;

import de.melinadanhier.projectflow.plancontainer.project.model.ProjectClassification;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectSubCategory;
import de.melinadanhier.projectflow.plancontainer.model.SortMode;
import de.melinadanhier.projectflow.plancontainer.model.StructureMode;
import de.melinadanhier.projectflow.plancontainer.project.dto.ProjectCreateForm;
import de.melinadanhier.projectflow.plancontainer.project.model.CreationType;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory;
import de.melinadanhier.projectflow.wizard.dto.ProjectTimeFrameType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class ProjectWizardState implements Serializable, ProjectClassification {

    @Serial
    private static final long serialVersionUID = 2L;

    private UUID userId;
    private String title;
    private String description;
    private TemplateCategory category = TemplateCategory.OTHER;
    private String otherProjectTypeDescription;

    private ProjectSubCategory subcategory;
    private CollaborationMode collaborationMode;
    private CreationType creationType;
    private LocalDate startDate;
    private LocalDate endDate;
    private ProjectTimeFrameType timeFrameType = ProjectTimeFrameType.NONE;
    private Integer durationDays;
    private StructureMode structureMode;
    private SortMode sortMode;
    private String projectGoal;
    private String constraints;
    private String additionalInformation;
    private boolean aiDetailsCompleted;
    private UUID completionToken;

    public ProjectCreateForm toProjectCreateForm() {
        ProjectCreateForm form = new ProjectCreateForm();
        form.setTitle(title);
        form.setDescription(description);
        form.setCategory(category);
        form.setOtherProjectTypeDescription(otherProjectTypeDescription);
        form.setSubcategory(subcategory);
        form.setCollaborationMode(collaborationMode);
        form.setCreationType(creationType);
        form.setStartDate(startDate);
        form.setEndDate(endDate);
        form.setStructureMode(structureMode);
        form.setSortMode(sortMode);
        return form;
    }
}
