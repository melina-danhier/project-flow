package de.melinadanhier.projectflow.plancontainer.project.dto;

import de.melinadanhier.projectflow.common.validation.UpdateValidation;
import de.melinadanhier.projectflow.plancontainer.project.validation.ValidProjectClassification;
import de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import jakarta.validation.constraints.AssertTrue;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectSubCategory;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectClassification;
import de.melinadanhier.projectflow.plancontainer.model.SortMode;
import de.melinadanhier.projectflow.plancontainer.model.StructureMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@ValidProjectClassification
public class ProjectUpdateForm implements ProjectClassification {

    @NotBlank
    @Size(max = 100)
    private String title;

    @Size(max = 2000)
    private String description;

    @NotNull(message = "Bitte wähle eine Oberkategorie aus.")
    private TemplateCategory category;
    private ProjectSubCategory subcategory;
    @Size(max = 100, message = "Die Beschreibung darf höchstens 100 Zeichen lang sein.")
    private String otherProjectTypeDescription;

    @NotNull(message = "Bitte wähle Einzel- oder Gruppenprojekt aus.")
    private CollaborationMode collaborationMode;

    private boolean confirmIndividualConversion;

    @AssertTrue(message = "Bitte wähle Einzel- oder Gruppenprojekt aus.")
    public boolean isProjectCollaborationModeValid() {
        return collaborationMode == null || collaborationMode != CollaborationMode.BOTH;
    }

    private LocalDate startDate;
    private LocalDate endDate;
    private StructureMode structureMode;
    private SortMode sortMode;

    @PositiveOrZero
    @NotNull(groups = UpdateValidation.class)
    private Long lockVersion;
}
