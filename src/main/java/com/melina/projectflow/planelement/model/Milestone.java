package com.melina.projectflow.planelement.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "milestones")
@PrimaryKeyJoinColumn(name = "id")
@Getter
@Setter
@NoArgsConstructor
public class Milestone extends PlanElement {

    @Column(name = "due_date")
    private LocalDate dueDate;

    @PositiveOrZero
    @Column(name = "relative_due_day")
    private Integer relativeDueDay;

    @Column(name = "completed", nullable = false)
    private boolean completed;
}
