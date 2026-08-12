package com.melina.projectflow.planelement.dto;

import com.melina.projectflow.planelement.model.ElementOrigin;
import com.melina.projectflow.planelement.model.TaskPriority;
import com.melina.projectflow.planelement.model.TaskStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class TaskDetailsDto {

    private UUID id;
    private UUID planContainerId;
    private UUID planSectionId;
    private String title;
    private String description;
    private int sortOrder;
    private ElementOrigin origin;
    private boolean hasCriticalAssumption;
    private TaskStatus status;
    private TaskPriority priority;
    private LocalDate startDate;
    private LocalDate dueDate;
    private Integer relativeStartDay;
    private Integer relativeDueDay;
    private UUID assigneeId;
    private Set<UUID> prerequisiteIds = new LinkedHashSet<>();
    private Instant completedAt;
}
