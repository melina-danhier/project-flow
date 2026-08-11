package com.melina.projectflow.project.persistence;

import com.melina.projectflow.project.domain.TaskPriority;
import com.melina.projectflow.project.domain.TaskStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "tasks")
@PrimaryKeyJoinColumn(name = "id")
@Getter
@Setter
@NoArgsConstructor
public class Task extends PlanElement {

    @Setter(AccessLevel.NONE)
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TaskStatus status = TaskStatus.OPEN;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private TaskPriority priority = TaskPriority.MEDIUM;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @PositiveOrZero
    @Column(name = "relative_start_day")
    private Integer relativeStartDay;

    @PositiveOrZero
    @Column(name = "relative_due_day")
    private Integer relativeDueDay;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private ProjectMember assignee;

    @Setter(AccessLevel.NONE)
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "task_prerequisites",
            joinColumns = @JoinColumn(name = "successor_task_id", nullable = false),
            inverseJoinColumns = @JoinColumn(name = "prerequisite_task_id", nullable = false),
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_task_prerequisites_pair",
                    columnNames = {"successor_task_id", "prerequisite_task_id"}
            )
    )
    private Set<Task> prerequisites = new LinkedHashSet<>();

    @Column(name = "completed_at")
    private Instant completedAt;

    public void setStatus(TaskStatus status) {
        TaskStatus newStatus = Objects.requireNonNull(status, "status must not be null");
        if (newStatus == TaskStatus.COMPLETED && this.status != TaskStatus.COMPLETED) {
            completedAt = Instant.now();
        } else if (newStatus != TaskStatus.COMPLETED) {
            completedAt = null;
        }
        this.status = newStatus;
    }

    public void changeStatus(TaskStatus status) {
        setStatus(status);
    }

    public void addPrerequisite(Task prerequisite) {
        prerequisites.add(prerequisite);
    }

    public void removePrerequisite(Task prerequisite) {
        prerequisites.remove(prerequisite);
    }
}
