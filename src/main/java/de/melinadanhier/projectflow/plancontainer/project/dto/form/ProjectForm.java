package de.melinadanhier.projectflow.plancontainer.project.dto.form;

import de.melinadanhier.projectflow.plancontainer.model.SortMode;
import de.melinadanhier.projectflow.plancontainer.model.StructureMode;
import de.melinadanhier.projectflow.plancontainer.project.model.classification.ProjectClassification;
import de.melinadanhier.projectflow.plancontainer.project.model.classification.ProjectSubCategory;
import de.melinadanhier.projectflow.plancontainer.project.model.collaboration.ProjectCollaboration;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.ProjectCategory;
import de.melinadanhier.projectflow.plancontainer.project.model.TaskProgressDisplay;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

@Getter
@Setter
public abstract class ProjectForm implements ProjectClassification, ProjectCollaboration {

    @NotBlank
    @Size(max = 100)
    private String title;

    @Size(max = 2000)
    private String description;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    @Positive(message = "Die geplante Dauer muss mindestens einen Tag betragen.")
    private Integer plannedDurationDays;

    @NotNull(message = "Bitte wähle eine Oberkategorie aus.")
    private ProjectCategory category;

    private ProjectSubCategory subcategory;

    @NotNull(message = "Bitte wähle Einzel- oder Gruppenprojekt aus.")
    private CollaborationMode collaborationMode;

    private StructureMode structureMode;
    private SortMode sortMode;
    private TaskProgressDisplay taskProgressDisplay;

    @AssertTrue(message = "Das Projektende darf nicht vor dem Projektstart liegen.")
    public boolean isDateRangeValid() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }

    @AssertTrue(message = "Startdatum, Enddatum und geplante Dauer widersprechen sich.")
    public boolean isPlannedDurationConsistent() {
        return startDate == null || endDate == null || plannedDurationDays == null
                || java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1 == plannedDurationDays;
    }

    @AssertTrue(message = "Bitte wähle Einzel- oder Gruppenprojekt aus.")
    public boolean isProjectCollaborationModeValid() {
        return collaborationMode == null || collaborationMode != CollaborationMode.BOTH;
    }
}
