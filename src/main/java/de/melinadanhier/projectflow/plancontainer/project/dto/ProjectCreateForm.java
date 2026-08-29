package de.melinadanhier.projectflow.plancontainer.project.dto;

import de.melinadanhier.projectflow.plancontainer.project.validation.ValidProjectClassification;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectClassification;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectSubCategory;
import de.melinadanhier.projectflow.plancontainer.model.SortMode;
import de.melinadanhier.projectflow.plancontainer.model.StructureMode;
import de.melinadanhier.projectflow.plancontainer.project.model.CreationType;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@ValidProjectClassification
public class ProjectCreateForm implements ProjectClassification {

    @NotBlank
    @Size(max = 100)
    private String title;

    @Size(max = 2000)
    private String description;

    private LocalDate startDate;
    private LocalDate endDate;

    @NotNull
    private TemplateCategory category = TemplateCategory.OTHER;

    @Size(max = 100)
    private String otherProjectTypeDescription;

    private ProjectSubCategory subcategory;

    @NotNull
    private CollaborationMode collaborationMode;

    @NotNull
    private CreationType creationType;

    private StructureMode structureMode;
    private SortMode sortMode;

    @AssertTrue(message = "Das Projektende darf nicht vor dem Projektstart liegen.")
    public boolean isDateRangeValid() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }

    @AssertTrue(message = "Bitte wähle Einzel- oder Gruppenprojekt aus.")
    public boolean isProjectCollaborationModeValid() {
        return collaborationMode == null || collaborationMode != CollaborationMode.BOTH;
    }
}
