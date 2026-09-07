package de.melinadanhier.projectflow.wizard.dto;

import de.melinadanhier.projectflow.plancontainer.project.validation.ValidProjectClassification;
import de.melinadanhier.projectflow.plancontainer.project.model.classification.ProjectClassification;
import de.melinadanhier.projectflow.plancontainer.project.model.classification.ProjectSubCategory;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.ProjectCategory;
import de.melinadanhier.projectflow.wizard.model.ProjectWizardState;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@ValidProjectBasics
@ValidProjectClassification(requireOtherDescription = false)
public class ProjectBasicsForm implements ProjectClassification {

    @NotBlank(message = "Bitte gib deinem Projekt einen Titel.")
    @Size(max = 100, message = "Der Titel darf höchstens 100 Zeichen lang sein.")
    private String title;

    @Size(max = 2000, message = "Die Beschreibung darf höchstens 2000 Zeichen lang sein.")
    private String description;

    @NotNull(message = "Bitte wähle eine Oberkategorie aus.")
    private ProjectCategory category = ProjectCategory.OTHER;

    private ProjectSubCategory subcategory;

    @Size(max = 100, message = "Die Beschreibung darf höchstens 100 Zeichen lang sein.")
    private String otherProjectTypeDescription;

    @NotNull(message = "Bitte wähle Einzel- oder Gruppenprojekt aus.")
    private CollaborationMode collaborationMode;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    @Positive(message = "Die Dauer muss mindestens einen Tag betragen.")
    private Integer durationDays;

    @Size(max = 1000, message = "Die verfügbare Arbeitszeit darf höchstens 1000 Zeichen lang sein.")
    private String availableWorkingTime;

    public static ProjectBasicsForm from(ProjectWizardState state) {
        ProjectBasicsForm form = new ProjectBasicsForm();
        form.setTitle(state.getTitle());
        form.setDescription(state.getDescription());
        form.setCategory(state.getCategory() == null ? ProjectCategory.OTHER : state.getCategory());
        form.setOtherProjectTypeDescription(state.getOtherProjectTypeDescription());
        form.setSubcategory(state.getSubcategory());
        form.setCollaborationMode(state.getCollaborationMode());
        form.setStartDate(state.getStartDate());
        form.setEndDate(state.getEndDate());
        form.setDurationDays(state.getDurationDays());
        form.setAvailableWorkingTime(state.getAvailableWorkingTime());
        return form;
    }
}
