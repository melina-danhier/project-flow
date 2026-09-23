package de.melinadanhier.projectflow.wizard.model;

import de.melinadanhier.projectflow.plancontainer.project.model.classification.ProjectClassification;
import de.melinadanhier.projectflow.plancontainer.project.model.classification.ProjectSubCategory;
import de.melinadanhier.projectflow.plancontainer.model.SortMode;
import de.melinadanhier.projectflow.plancontainer.model.StructureMode;
import de.melinadanhier.projectflow.plancontainer.project.dto.form.ProjectCreateForm;
import de.melinadanhier.projectflow.plancontainer.project.model.lifecycle.CreationType;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.ProjectCategory;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
public class ProjectWizardState implements Serializable, ProjectClassification {

    @Serial
    private static final long serialVersionUID = 5L;

    private UUID userId;
    private String title;
    private String description;
    private ProjectCategory category = ProjectCategory.OTHER;
    private ProjectSubCategory subcategory;
    private CollaborationMode collaborationMode;
    private CreationType creationType;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer durationValue;
    private String durationUnit;
    private Integer durationDays;
    private String availableWorkingTime;
    private StructureMode structureMode;
    private SortMode sortMode;
    private String projectGoal;
    private String constraints;
    private String additionalInformation;
    private boolean aiDetailsCompleted;
    private Map<String, String> projectSpecificAnswers = new LinkedHashMap<>();
    private UUID completionToken;
    private UUID activeWorkflowId;
    private UUID selectedTemplateId;

    public void setDurationDays(Integer durationDays) {
        this.durationDays = durationDays;
        if (durationDays == null) {
            this.durationValue = null;
            this.durationUnit = null;
            return;
        }
        if (this.durationValue != null && matchesDuration(this.durationValue, this.durationUnit, durationDays)) {
            return;
        }
        this.durationValue = durationDays;
        this.durationUnit = "DAYS";
    }

    private boolean matchesDuration(Integer val, String unit, Integer days) {
        if (val == null || unit == null || days == null) return false;
        if ("WEEKS".equalsIgnoreCase(unit)) return val * 7 == days;
        if ("MONTHS".equalsIgnoreCase(unit)) return val * 30 == days;
        if ("DAYS".equalsIgnoreCase(unit)) return val.equals(days);
        return false;
    }

    public Map<String, String> getProjectSpecificAnswers() {
        if (projectSpecificAnswers == null) {
            projectSpecificAnswers = new LinkedHashMap<>();
        }
        return projectSpecificAnswers;
    }

    public ProjectCreateForm toProjectCreateForm() {
        ProjectCreateForm form = new ProjectCreateForm();
        form.setTitle(title);
        form.setDescription(description);
        form.setCategory(category);
        form.setSubcategory(subcategory);
        form.setCollaborationMode(collaborationMode);
        form.setCreationType(creationType);
        form.setStartDate(startDate);
        form.setEndDate(endDate);
        form.setPlannedDurationDays(durationDays);
        form.setStructureMode(structureMode);
        form.setSortMode(sortMode);
        return form;
    }
}
