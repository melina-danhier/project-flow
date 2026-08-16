package de.melinadanhier.projectflow.plancontainer.project.dto;

import de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Allgemeine Projektdaten des Wizards. Zusätzliche Angaben für die KI
 * werden bewusst nicht in diesem Formular geführt.
 */
@Getter
@Setter
@NoArgsConstructor
public class ProjectBasicsForm {

    @NotBlank(message = "Bitte gib deinem Projekt einen Titel.")
    @Size(max = 100, message = "Der Titel darf höchstens 100 Zeichen lang sein.")
    private String title;

    @NotNull(message = "Bitte wähle eine Oberkategorie aus.")
    private TemplateCategory category = TemplateCategory.OTHER;

    @Size(max = 100, message = "Die Unterkategorie darf höchstens 100 Zeichen lang sein.")
    private String subcategory;

    @Size(max = 100, message = "Die Beschreibung darf höchstens 100 Zeichen lang sein.")
    private String otherProjectTypeDescription;

    @NotNull(message = "Bitte wähle aus, welche Zeitangaben du machen möchtest.")
    private ProjectTimeFrameType timeFrameType = ProjectTimeFrameType.NONE;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    @Positive(message = "Die Dauer muss mindestens einen Tag betragen.")
    private Integer durationDays;

    public static ProjectBasicsForm from(ProjectCreationFlowState state) {
        ProjectBasicsForm form = new ProjectBasicsForm();
        form.setTitle(state.getTitle());
        form.setCategory(state.getCategory() == null ? TemplateCategory.OTHER : state.getCategory());
        if (form.getCategory() == TemplateCategory.OTHER) {
            form.setOtherProjectTypeDescription(state.getProjectType());
        } else {
            form.setSubcategory(state.getProjectType());
        }
        form.setTimeFrameType(state.getTimeFrameType() == null
                ? ProjectTimeFrameType.NONE
                : state.getTimeFrameType());
        form.setDurationDays(state.getDurationDays());
        switch (form.getTimeFrameType()) {
            case START_AND_END -> {
                form.setStartDate(state.getStartDate());
                form.setEndDate(state.getEndDate());
            }
            case START_AND_DURATION -> form.setStartDate(state.getStartDate());
            case END_AND_DURATION -> form.setEndDate(state.getEndDate());
            case NONE -> {
                // Keine Datumsfelder anzeigen.
            }
        }
        return form;
    }

    @AssertTrue(message = "Bitte beschreibe kurz, um welche Art von Projekt es sich handelt.")
    public boolean isProjectTypeValid() {
        return category == null
                || category != TemplateCategory.OTHER
                || otherProjectTypeDescription != null && !otherProjectTypeDescription.isBlank();
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

    @AssertTrue(message = "Bitte fülle genau die Zeitangaben der gewählten Variante aus.")
    public boolean isTimeFrameValid() {
        if (timeFrameType == null) {
            return true;
        }
        return switch (timeFrameType) {
            case START_AND_END -> startDate != null
                    && endDate != null
                    && durationDays == null
                    && !endDate.isBefore(startDate);
            case START_AND_DURATION -> startDate != null
                    && endDate == null
                    && durationDays != null
                    && durationDays > 0;
            case END_AND_DURATION -> startDate == null
                    && endDate != null
                    && durationDays != null
                    && durationDays > 0;
            case NONE -> startDate == null && endDate == null && durationDays == null;
        };
    }
}
