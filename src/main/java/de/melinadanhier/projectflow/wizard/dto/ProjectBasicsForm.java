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
@ValidProjectClassification
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
    private CollaborationMode collaborationMode = CollaborationMode.INDIVIDUAL;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    @Positive(message = "Die Dauer muss mindestens 1 betragen.")
    private Integer durationValue;

    private String durationUnit = "DAYS";

    @Positive(message = "Die Dauer muss mindestens einen Tag betragen.")
    private Integer durationDays;

    public void setDurationValue(Integer durationValue) {
        this.durationValue = durationValue;
        syncDurationDays();
    }

    public void setDurationUnit(String durationUnit) {
        this.durationUnit = (durationUnit == null || durationUnit.isBlank()) ? "DAYS" : durationUnit.trim().toUpperCase();
        syncDurationDays();
    }

    public void setDurationDays(Integer durationDays) {
        if (durationDays == null) {
            this.durationDays = null;
            this.durationValue = null;
            return;
        }
        if (this.durationValue != null) {
            syncDurationDays();
            return;
        }
        this.durationDays = durationDays;
        if (durationDays >= 30 && durationDays % 30 == 0) {
            this.durationValue = durationDays / 30;
            this.durationUnit = "MONTHS";
        } else if (durationDays >= 7 && durationDays % 7 == 0) {
            this.durationValue = durationDays / 7;
            this.durationUnit = "WEEKS";
        } else {
            this.durationValue = durationDays;
            this.durationUnit = "DAYS";
        }
    }

    private void syncDurationDays() {
        if (durationValue == null) {
            this.durationDays = null;
        } else if ("WEEKS".equalsIgnoreCase(durationUnit)) {
            this.durationDays = durationValue * 7;
        } else if ("MONTHS".equalsIgnoreCase(durationUnit)) {
            this.durationDays = durationValue * 30;
        } else {
            this.durationDays = durationValue;
        }
    }

    @Size(max = 1000, message = "Die verfügbare Arbeitszeit darf höchstens 1000 Zeichen lang sein.")
    private String availableWorkingTime;

    public static ProjectBasicsForm from(ProjectWizardState state) {
        ProjectBasicsForm form = new ProjectBasicsForm();
        form.setTitle(state.getTitle());
        form.setDescription(state.getDescription());
        form.setCategory(state.getCategory() == null ? ProjectCategory.OTHER : state.getCategory());
        form.setOtherProjectTypeDescription(state.getOtherProjectTypeDescription());
        form.setSubcategory(state.getSubcategory());
        form.setCollaborationMode(state.getCollaborationMode() == null ? CollaborationMode.INDIVIDUAL : state.getCollaborationMode());
        form.setStartDate(state.getStartDate());
        form.setEndDate(state.getEndDate());
        form.setDurationDays(state.getDurationDays());
        form.setAvailableWorkingTime(state.getAvailableWorkingTime());
        return form;
    }
}
