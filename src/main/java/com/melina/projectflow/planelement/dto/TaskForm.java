package com.melina.projectflow.planelement.dto;

import com.melina.projectflow.planelement.model.TaskPriority;
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

    @NotNull
    private UUID planContainerId;
    private UUID planSectionId;

    @NotBlank
    @Size(max = 100)
    private String title;

    @Size(max = 2000)
    private String description;

    @PositiveOrZero
    private int sortOrder;

    @NotNull
    private TaskPriority priority;

    private LocalDate startDate;
    private LocalDate dueDate;

    @PositiveOrZero
    private Integer relativeStartDay;

    @PositiveOrZero
    private Integer relativeDueDay;

    private UUID assigneeId;
    private Set<UUID> prerequisiteIds = new LinkedHashSet<>();
}
