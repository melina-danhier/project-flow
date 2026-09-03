package de.melinadanhier.projectflow.planelement.dto;

import de.melinadanhier.projectflow.common.validation.UpdateValidation;
import de.melinadanhier.projectflow.planelement.model.TaskPriority;
import de.melinadanhier.projectflow.planelement.model.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    private LocalDate startDate;
    private LocalDate dueDate;

    private UUID assigneeId;

    @PositiveOrZero
    @NotNull(groups = UpdateValidation.class)
    private Long lockVersion;
}
