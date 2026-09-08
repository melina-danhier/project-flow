package de.melinadanhier.projectflow.draft.dto.editing;

import de.melinadanhier.projectflow.planelement.model.TaskPriority;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

import static de.melinadanhier.projectflow.ai.validation.AiResponseLimits.MAX_ESTIMATED_HOURS;

@Getter
@Setter
public class DraftTaskForm {
    @NotNull @PositiveOrZero private Long lockVersion;
    @NotBlank @Size(max = 100) private String title;
    @Size(max = 2000) private String description;
    private LocalDate startDate;
    private LocalDate dueDate;
    @Positive @Max(MAX_ESTIMATED_HOURS)
    private Integer estimatedHours;
    @NotNull private TaskPriority priority;

    @AssertTrue(message = "Die Deadline darf nicht vor dem Startdatum liegen.")
    public boolean isDateRangeValid() {
        return startDate == null || dueDate == null || !dueDate.isBefore(startDate);
    }
}
