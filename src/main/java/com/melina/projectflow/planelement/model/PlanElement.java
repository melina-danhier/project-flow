package com.melina.projectflow.planelement.model;

import com.melina.projectflow.common.model.MutableEntity;
import com.melina.projectflow.plancontainer.model.PlanContainer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "plan_elements")
@Inheritance(strategy = InheritanceType.JOINED)
@Getter
@Setter
@NoArgsConstructor
public abstract class PlanElement extends MutableEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_container_id", nullable = false)
    private PlanContainer planContainer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_section_id")
    private PlanSection planSection;

    @NotBlank
    @Size(max = 100)
    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Size(max = 2000)
    @Column(name = "description", length = 2000)
    private String description;

    @PositiveOrZero
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, length = 20)
    private ElementOrigin origin;

    @Column(name = "has_critical_assumption", nullable = false)
    private boolean hasCriticalAssumption;
}
