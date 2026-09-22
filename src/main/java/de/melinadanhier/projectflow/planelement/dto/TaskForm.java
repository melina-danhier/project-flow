package de.melinadanhier.projectflow.planelement.dto;

import de.melinadanhier.projectflow.common.validation.UpdateValidation;
import de.melinadanhier.projectflow.planelement.model.TaskPriority;
import de.melinadanhier.projectflow.planelement.model.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class TaskForm {

    private UUID planSectionId;

    @NotBlank
    @Size(max = 100)
    private String title;

    @Size(max = 2000)
    private String description;

    @PositiveOrZero
    private Integer sortOrder;

    @NotNull
    private TaskPriority priority;

    private TaskStatus status;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dueDate;

    @PositiveOrZero(message = "Die Stunden dürfen nicht negativ sein.")
    private Integer effortHours;

    @PositiveOrZero(message = "Die Minuten dürfen nicht negativ sein.")
    private Integer effortMinutes;

    private Set<UUID> assigneeIds = new LinkedHashSet<>();

    @PositiveOrZero
    @NotNull(groups = UpdateValidation.class)
    private Long lockVersion;

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
        return minutes == null || minutes <= 600_000;
    }

    @AssertTrue(message = "Das Fälligkeitsdatum darf nicht vor dem Startdatum liegen.")
    public boolean isDateRangeValid() {
        return startDate == null || dueDate == null || !dueDate.isBefore(startDate);
    }
}
