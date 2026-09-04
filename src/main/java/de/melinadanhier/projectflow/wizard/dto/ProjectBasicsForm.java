package de.melinadanhier.projectflow.wizard.dto;

import de.melinadanhier.projectflow.plancontainer.project.validation.ValidProjectClassification;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectClassification;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectSubCategory;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory;
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
    private TemplateCategory category = TemplateCategory.OTHER;

    private ProjectSubCategory subcategory;

    @Size(max = 100, message = "Die Beschreibung darf höchstens 100 Zeichen lang sein.")
    private String otherProjectTypeDescription;

    @NotNull(message = "Bitte wähle Einzel- oder Gruppenprojekt aus.")
    private CollaborationMode collaborationMode;

    @NotNull(message = "Bitte wähle aus, welche Zeitangaben du machen möchtest.")
    private ProjectTimeFrameType timeFrameType = ProjectTimeFrameType.NONE;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    @Positive(message = "Die Dauer muss mindestens einen Tag betragen.")
    private Integer durationDays;

    public static ProjectBasicsForm from(ProjectWizardState state) {
        ProjectBasicsForm form = new ProjectBasicsForm();
        form.setTitle(state.getTitle());
        form.setDescription(state.getDescription());
        form.setCategory(state.getCategory() == null ? TemplateCategory.OTHER : state.getCategory());
        form.setOtherProjectTypeDescription(state.getOtherProjectTypeDescription());
        form.setSubcategory(state.getSubcategory());
        form.setCollaborationMode(state.getCollaborationMode());
        form.setTimeFrameType(state.getTimeFrameType() == null
                ? ProjectTimeFrameType.NONE : state.getTimeFrameType());
        form.setDurationDays(state.getDurationDays());
        switch (form.getTimeFrameType()) {
            case START_AND_END -> {
                form.setStartDate(state.getStartDate());
                form.setEndDate(state.getEndDate());
            }
            case START_AND_DURATION -> form.setStartDate(state.getStartDate());
            case END_AND_DURATION -> form.setEndDate(state.getEndDate());
            case NONE -> { }
        }
        return form;
    }

    public boolean isOtherCategory() {
        return category == TemplateCategory.OTHER;
    }

    public boolean isStartDateInputActive() {
        return timeFrameType == ProjectTimeFrameType.START_AND_END
                || timeFrameType == ProjectTimeFrameType.START_AND_DURATION;
    }

    public boolean isEndDateInputActive() {
        return timeFrameType == ProjectTimeFrameType.START_AND_END
                || timeFrameType == ProjectTimeFrameType.END_AND_DURATION;
    }

    public boolean isDurationInputActive() {
        return timeFrameType == ProjectTimeFrameType.START_AND_DURATION
                || timeFrameType == ProjectTimeFrameType.END_AND_DURATION;
    }
}
