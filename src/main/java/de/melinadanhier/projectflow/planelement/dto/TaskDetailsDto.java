package de.melinadanhier.projectflow.planelement.dto;

import de.melinadanhier.projectflow.planelement.model.ElementOrigin;
import de.melinadanhier.projectflow.planelement.model.TaskPriority;
import de.melinadanhier.projectflow.planelement.model.TaskStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import de.melinadanhier.projectflow.plancontainer.project.dto.ProjectMemberDto;
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
    private UUID assigneeUserId;
    private String assigneeDisplayName;
    private Set<UUID> prerequisiteIds = new LinkedHashSet<>();
    private List<TaskReferenceDto> predecessors = new ArrayList<>();
    private List<TaskReferenceDto> successors = new ArrayList<>();
    private List<TaskReferenceDto> availablePrerequisites = new ArrayList<>();
    private List<ProjectMemberDto> availableAssignees = new ArrayList<>();
    private List<SectionDto> availableSections = new ArrayList<>();
    private int affectedDependencyCount;
    private boolean editable;
    private Instant completedAt;
    private long lockVersion;
}
