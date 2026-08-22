package de.melinadanhier.projectflow.draft.model;

import de.melinadanhier.projectflow.planelement.model.TaskPriority;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "draft_tasks")
@PrimaryKeyJoinColumn(name = "id")
@Getter
@Setter
@NoArgsConstructor
public class DraftTask extends DraftPlanElement {

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private TaskPriority priority = TaskPriority.MEDIUM;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @jakarta.validation.constraints.Positive
    @Column(name = "estimated_hours")
    private Integer estimatedHours;

    @Setter(AccessLevel.NONE)
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "draft_task_prerequisites",
            joinColumns = @JoinColumn(name = "successor_draft_task_id", nullable = false),
            inverseJoinColumns = @JoinColumn(name = "prerequisite_draft_task_id", nullable = false),
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_draft_task_prerequisites_pair",
                    columnNames = {"successor_draft_task_id", "prerequisite_draft_task_id"}
            )
    )
    private Set<DraftTask> prerequisites = new LinkedHashSet<>();

    public void addPrerequisite(DraftTask prerequisite) {
        prerequisites.add(prerequisite);
    }

    public void removePrerequisite(DraftTask prerequisite) {
        prerequisites.remove(prerequisite);
    }
}
