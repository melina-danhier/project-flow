package de.melinadanhier.projectflow.draft.dto.editing;

import de.melinadanhier.projectflow.planelement.model.TaskPriority;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.util.UUID;

import static de.melinadanhier.projectflow.ai.validation.AiResponseLimits.MAX_ESTIMATED_MINUTES;

@Getter
@Setter
public class DraftTaskForm {
    @NotNull @PositiveOrZero private Long lockVersion;
    @NotBlank @Size(max = 100) private String title;
    @Size(max = 2000) private String description;
    private UUID draftSectionId;
    private boolean sectionSelectionPresent;
    private LocalDate startDate;
    private LocalDate dueDate;
    @PositiveOrZero(message = "Die Stunden dürfen nicht negativ sein.")
    private Integer effortHours;

    @PositiveOrZero(message = "Die Minuten dürfen nicht negativ sein.")
    private Integer effortMinutes;

    @NotNull private TaskPriority priority;

    public Integer getEstimatedMinutes() {
        if (effortHours == null && effortMinutes == null) {
            return null;
        }
        long h = effortHours != null ? effortHours : 0;
        long m = effortMinutes != null ? effortMinutes : 0;
        long total = h * 60 + m;
        if (total <= 0) {
            return null;
        }
        return (int) Math.min(total, Integer.MAX_VALUE);
    }

    public void setEstimatedMinutes(Integer minutes) {
        if (minutes == null || minutes <= 0) {
            this.effortHours = null;
            this.effortMinutes = null;
        } else {
            this.effortHours = minutes / 60;
            this.effortMinutes = minutes % 60;
        }
    }

    @AssertTrue(message = "Der geschätzte Gesamtaufwand darf maximal 600.000 Minuten (10.000 Stunden) betragen.")
    public boolean isEffortValid() {
        Integer minutes = getEstimatedMinutes();
        return minutes == null || minutes <= MAX_ESTIMATED_MINUTES;
    }

    @AssertTrue(message = "Die Deadline darf nicht vor dem Startdatum liegen.")
    public boolean isDateRangeValid() {
        return startDate == null || dueDate == null || !dueDate.isBefore(startDate);
    }
}
